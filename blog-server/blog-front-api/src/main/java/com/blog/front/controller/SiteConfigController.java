package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.SiteConfig;
import com.blog.common.mapper.SiteConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/site-config")
@RequiredArgsConstructor
public class SiteConfigController {

    private final SiteConfigMapper siteConfigMapper;

    @GetMapping("/public")
    public Result<Map<String, String>> publicConfig() {
        List<SiteConfig> list = siteConfigMapper.selectList(null);
        Map<String, String> map = new HashMap<>();
        for (SiteConfig c : list) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        if (!map.containsKey("siteName")) map.put("siteName", "好啵博客");
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
    public Result<?> save(@RequestBody Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            SiteConfig exist = siteConfigMapper.selectOne(
                    new LambdaQueryWrapper<SiteConfig>().eq(SiteConfig::getConfigKey, entry.getKey()));
            if (exist != null) {
                exist.setConfigValue(entry.getValue());
                siteConfigMapper.updateById(exist);
            } else {
                SiteConfig c = new SiteConfig();
                c.setConfigKey(entry.getKey());
                c.setConfigValue(entry.getValue());
                siteConfigMapper.insert(c);
            }
        }
        return Result.ok();
    }
}
