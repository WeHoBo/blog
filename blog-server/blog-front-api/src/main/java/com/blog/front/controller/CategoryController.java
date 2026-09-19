package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.constant.CategoryConstants;
import com.blog.common.dto.Result;
import com.blog.common.entity.Category;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.vo.CategoryArticleCount;
import com.blog.common.vo.CategoryTreeVO;
import com.blog.front.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;

    /** 一次 GROUP BY 统计「各分类直属的已发布文章数」，替代逐分类 selectCount 的 N+1 查询 */
    private Map<Long, Integer> directArticleCounts() {
        Map<Long, Integer> counts = new HashMap<>();
        for (CategoryArticleCount row : articleMapper.countPublishedByCategory()) {
            if (row.getCategoryId() != null) {
                counts.put(row.getCategoryId(), row.getCnt() == null ? 0 : row.getCnt().intValue());
            }
        }
        return counts;
    }

    /** 为列表填充 articleCount：包含自身及所有子孙分类的已发布文章总数 */
    private void fillArticleCounts(List<Category> list) {
        Map<Long, Integer> direct = directArticleCounts();
        Map<Long, List<Category>> childrenMap = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId));
        Map<Long, Integer> memo = new HashMap<>();
        for (Category cat : list) {
            cat.setArticleCount(subtreeCount(cat.getId(), direct, childrenMap, memo, new HashSet<>()));
        }
    }

    private int subtreeCount(Long categoryId, Map<Long, Integer> direct,
                             Map<Long, List<Category>> childrenMap,
                             Map<Long, Integer> memo, Set<Long> visiting) {
        if (categoryId == null) {
            return 0;
        }
        Integer cached = memo.get(categoryId);
        if (cached != null) {
            return cached;
        }
        // 防御脏数据造成的父子环，避免无限递归
        if (!visiting.add(categoryId)) {
            return 0;
        }
        int count = direct.getOrDefault(categoryId, 0);
        List<Category> children = childrenMap.get(categoryId);
        if (children != null) {
            for (Category child : children) {
                count += subtreeCount(child.getId(), direct, childrenMap, memo, visiting);
            }
        }
        visiting.remove(categoryId);
        memo.put(categoryId, count);
        return count;
    }

    @GetMapping("/list")
    public Result<List<Category>> list() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        normalizeOrphans(list);
        fillArticleCounts(list);
        return Result.ok(list);
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> tree() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        normalizeOrphans(list);
        fillArticleCounts(list);
        return Result.ok(CategoryTreeVO.buildTree(list));
    }

    /**
     * 把 parent_id 指向「不存在的分类」的孤儿分类提升为一级分类（仅修改返回对象，不改库）。
     *
     * <p>历史遗留：早期删除一级分类时没有级联删除子分类，子分类的 parent_id 就此悬空。
     * 它们既不是根节点、也不属于任何可见的父节点，会被分类树整个丢掉 ——
     * 在后台「消失」到连删除按钮都没有。归一化后它们以一级分类出现，可正常级联删除。
     */
    private void normalizeOrphans(List<Category> list) {
        Set<Long> ids = list.stream().map(Category::getId).collect(Collectors.toSet());
        for (Category c : list) {
            Long parentId = c.getParentId();
            if (parentId != null && parentId != 0 && !ids.contains(parentId)) {
                c.setParentId(0L);
            }
        }
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Category> create(@Valid @RequestBody Category category) {
        if (StrUtil.isBlank(category.getSlug())) {
            category.setSlug(category.getName());
        }
        categoryMapper.insert(category);
        return Result.ok(category);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return Result.ok(category);
    }

    /**
     * 删除分类：连同其下<b>所有子孙分类</b>一起级联删除。
     *
     * <p>以前的实现只删当前这一条、把直属文章置空，于是删掉一级分类后，
     * 二级分类会变成「父分类已不存在」的孤儿（分类树里漂浮成一级分类）。
     *
     * <p>现在先把整棵子树收出来，把这些分类下的文章统一改挂到内置分类「未建档文章」
     * （含回收站里的文章，走原生 SQL），再整批删除。直接改挂而不是置 NULL：
     * 分类页文章数按 category_id 聚合，置 NULL 要等下次启动回填后才数得到。
     */
    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<Integer> delete(@PathVariable Long id) {
        Category target = categoryMapper.selectById(id);
        if (target == null) {
            return Result.fail(404, "分类不存在");
        }
        // 默认分类是无分类文章的兜底归属，删掉它会让「未建档文章」这个语义彻底失效
        if (CategoryConstants.DEFAULT_CATEGORY_NAME.equals(target.getName())) {
            return Result.fail(400, "「" + CategoryConstants.DEFAULT_CATEGORY_NAME + "」是系统内置分类，不能删除");
        }
        List<Long> ids = collectSubtreeIds(id);
        articleService.cascadeReassignToDefault(ids);
        categoryMapper.deleteBatchIds(ids);
        return Result.ok(ids.size());
    }

    /**
     * 收集以 rootId 为根的整棵分类子树 id（含自身）。
     *
     * <p>一次查出全部分类在内存里建 children 索引，避免按层递归产生 N+1 查询；
     * 用 visited 集合同时承担「防重复入队」和「防御脏数据的父子环」两个职责。
     */
    private List<Long> collectSubtreeIds(Long rootId) {
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (Category c : categoryMapper.selectList(null)) {
            if (c.getParentId() == null || c.getParentId() == 0) {
                continue;
            }
            childrenMap.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        visited.add(rootId);
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            ids.add(current);
            for (Long child : childrenMap.getOrDefault(current, List.of())) {
                if (visited.add(child)) {
                    queue.add(child);
                }
            }
        }
        return ids;
    }
}
