package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.SiteConfig;
import com.blog.common.mapper.SiteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@RestController
@RequestMapping("/api/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    /** 与 site_config.config_key VARCHAR(100) 保持一致 */
    private static final int MAX_KEY_LENGTH = 100;

    /** config_value 为 TEXT（约 64KB），这里再收紧一档，避免异常大的内容入库 */
    private static final int MAX_VALUE_LENGTH = 20000;
    private static final Set<String> PUBLIC_KEYS = Set.of("siteName", "siteDesc", "aboutContent",
            "bgImage", "bloggerName", "bloggerBio", "bloggerAvatar", "bloggerFocus", "aiName", "aiGreeting", "aiQuestions");

    private final SiteConfigMapper siteConfigMapper;

    @GetMapping("/public")
    public Result<Map<String, String>> publicConfig() {
        List<SiteConfig> list = siteConfigMapper.selectList(new LambdaQueryWrapper<SiteConfig>()
                .select(SiteConfig::getConfigKey, SiteConfig::getConfigValue).in(SiteConfig::getConfigKey, PUBLIC_KEYS));
        Map<String, String> map = new HashMap<>();
        for (SiteConfig c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        if (!map.containsKey("siteName")) map.put("siteName", "博观");
        if (!map.containsKey("siteDesc")) map.put("siteDesc", "一个程序员的个人技术博客");
        if (!map.containsKey("aboutContent")) map.put("aboutContent", "");
        return Result.ok(map);
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/list")
    public Result<List<SiteConfig>> list() {
        return Result.ok(siteConfigMapper.selectList(null));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/save")
    @Transactional
    public Result<?> save(@RequestBody Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            return Result.ok();
        }
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StrUtil.isBlank(key) || key.length() > MAX_KEY_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置项名称不合法");
            }
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置项 " + key + " 内容过长（上限 " + MAX_VALUE_LENGTH + " 字）");
            }
        }
        // 相同的键按相同顺序加锁，两个管理员同时保存不同字段不会互相覆盖。
        new TreeMap<>(configs).forEach(siteConfigMapper::upsert);
        return Result.ok();
    }
}
