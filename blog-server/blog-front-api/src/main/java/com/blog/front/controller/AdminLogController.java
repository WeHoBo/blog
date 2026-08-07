package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.OperationLog;
import com.blog.common.entity.User;
import com.blog.common.mapper.OperationLogMapper;
import com.blog.common.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/log")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class AdminLogController {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        Page<OperationLog> page = new Page<>(pageNum, pageSize);
        Page<OperationLog> logPage = operationLogMapper.selectPage(page,
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreateTime));

        Set<Long> userIds = new HashSet<>();
        for (OperationLog log : logPage.getRecords()) {
            if (log.getUserId() != null) userIds.add(log.getUserId());
        }
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
        Map<Long, String> userMap = new HashMap<>();
        for (User u : users) {
            userMap.put(u.getId(), u.getNickname() != null ? u.getNickname() : u.getUsername());
        }

        Page<Map<String, Object>> result = new Page<>(pageNum, pageSize, logPage.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (OperationLog log : logPage.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", log.getId());
            item.put("createTime", log.getCreateTime());
            item.put("action", log.getAction());
            item.put("username", log.getUserId() != null ? userMap.getOrDefault(log.getUserId(), "未知") : "系统");
            records.add(item);
        }
        result.setRecords(records);
        return Result.ok(result);
    }
}
