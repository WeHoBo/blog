package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.Category;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.vo.CategoryTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;

    private int countArticles(Long categoryId, Map<Long, List<Category>> childrenMap) {
        int count = 0;
        Long c = articleMapper.selectCount(
                new LambdaQueryWrapper<com.blog.common.entity.Article>()
                        .eq(com.blog.common.entity.Article::getCategoryId, categoryId)
                        .eq(com.blog.common.entity.Article::getStatus, 1)
                        .eq(com.blog.common.entity.Article::getIsDeleted, 0));
        count += c.intValue();
        List<Category> children = childrenMap.get(categoryId);
        if (children != null) {
            for (Category child : children) {
                count += countArticles(child.getId(), childrenMap);
            }
        }
        return count;
    }

    @GetMapping("/list")
    public Result<List<Category>> list() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        Map<Long, List<Category>> childrenMap = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId));
        for (Category cat : list) {
            cat.setArticleCount(countArticles(cat.getId(), childrenMap));
        }
        return Result.ok(list);
    }

    @GetMapping("/tree")
    public Result<List<CategoryTreeVO>> tree() {
        List<Category> list = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
        Map<Long, List<Category>> childrenMap = list.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() > 0)
                .collect(Collectors.groupingBy(Category::getParentId));
        for (Category cat : list) {
            cat.setArticleCount(countArticles(cat.getId(), childrenMap));
        }
        return Result.ok(CategoryTreeVO.buildTree(list));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        if (StrUtil.isBlank(category.getSlug())) {
            category.setSlug(category.getName());
        }
        categoryMapper.insert(category);
        return Result.ok(category);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        category.setId(id);
        categoryMapper.updateById(category);
        return Result.ok(category);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        categoryMapper.deleteById(id);
        return Result.ok();
    }
}
