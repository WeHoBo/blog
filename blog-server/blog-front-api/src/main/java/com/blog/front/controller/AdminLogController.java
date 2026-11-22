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

    /**
     * 操作审计列表。
     *
     * @param status     可选：1 只看成功 / 0 只看失败。传 null 表示不过滤。
     *                   ⭐「只看失败」是这次扩容的主要用途 —— 越权尝试、越权改别人文章
     *                   都会以失败记录（status=0）留在这里。
     * @param targetType 可选：article / comment / comment_report / user
     */
    @GetMapping("/list")
    public Result<Page<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String targetType) {

        LambdaQueryWrapper<OperationLog> query = new LambdaQueryWrapper<OperationLog>()
                .eq(status != null, OperationLog::getStatus, status)
                .eq(targetType != null && !targetType.isBlank(), OperationLog::getTargetType, targetType)
                .orderByDesc(OperationLog::getCreateTime);

        Page<OperationLog> logPage = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), query);

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
            // 以下四项是 2026-11-18 扩容新增：没有它们，「失败记录」这个说法就不成立
            item.put("status", log.getStatus());
            item.put("costMs", log.getCostMs());
            item.put("ip", log.getIp());
            item.put("requestId", log.getRequestId());
            item.put("targetType", log.getTargetType());
            item.put("targetId", log.getTargetId());
            records.add(item);
        }
        result.setRecords(records);
        return Result.ok(result);
    }
}
