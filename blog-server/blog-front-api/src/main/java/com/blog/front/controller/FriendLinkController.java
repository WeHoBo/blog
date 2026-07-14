package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.FriendLink;
import com.blog.common.mapper.FriendLinkMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friend-link")
@RequiredArgsConstructor
public class FriendLinkController {

    private final FriendLinkMapper friendLinkMapper;

    @GetMapping("/list")
    public Result<List<FriendLink>> list() {
        return Result.ok(friendLinkMapper.selectList(
                new LambdaQueryWrapper<FriendLink>()
                        .eq(FriendLink::getStatus, 1)
                        .orderByAsc(FriendLink::getSort)));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<FriendLink> create(@RequestBody FriendLink link) {
        friendLinkMapper.insert(link);
        return Result.ok(link);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<FriendLink> update(@PathVariable Long id, @RequestBody FriendLink link) {
        link.setId(id);
        friendLinkMapper.updateById(link);
        return Result.ok(link);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        friendLinkMapper.deleteById(id);
        return Result.ok();
    }
}
