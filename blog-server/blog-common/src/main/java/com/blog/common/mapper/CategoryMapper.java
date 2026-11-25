package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.entity.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
    /**
     * 收集以 rootId 为根的整棵分类子树 id（含自身）。
     *
     * <p>一次查出全部分类在内存里建 children 索引，避免按层递归产生 N+1 查询；
     * 用 visited 集合同时承担「防重复入队」和「防御脏数据的父子环」两个职责。
     */
    default List<Long> subtreeIds(Long rootId) {
        Map<Long, List<Long>> childrenMap = new HashMap<>();
        for (Category c : selectList(new LambdaQueryWrapper<Category>().select(Category::getId, Category::getParentId))) {
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
