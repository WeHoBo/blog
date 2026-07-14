package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.blog.common.dto.Result;
import com.blog.common.entity.Tag;
import com.blog.common.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagMapper tagMapper;

    @GetMapping("/list")
    public Result<List<Tag>> list() {
        return Result.ok(tagMapper.selectList(null));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Tag> create(@RequestBody Tag tag) {
        if (StrUtil.isBlank(tag.getSlug())) {
            tag.setSlug(tag.getName());
        }
        tagMapper.insert(tag);
        return Result.ok(tag);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Tag> update(@PathVariable Long id, @RequestBody Tag tag) {
        tag.setId(id);
        tagMapper.updateById(tag);
        return Result.ok(tag);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        tagMapper.deleteById(id);
        return Result.ok();
    }
}
