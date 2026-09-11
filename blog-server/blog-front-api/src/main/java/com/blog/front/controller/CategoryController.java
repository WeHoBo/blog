package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.vo.CategoryArticleCount;
import com.blog.common.vo.CategoryTreeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
        fillArticleCounts(list);
        return Result.ok(list);
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> tree() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        fillArticleCounts(list);
        return Result.ok(CategoryTreeVO.buildTree(list));
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

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        // 先把该分类下的文章置为「未分类」，避免留下指向已删除分类的孤儿数据
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getCategoryId, id)
                .set(Article::getCategoryId, null));
        categoryMapper.deleteById(id);
        return Result.ok();
    }
}
