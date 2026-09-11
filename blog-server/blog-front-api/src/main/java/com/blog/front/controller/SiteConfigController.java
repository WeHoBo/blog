package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.dto.Result;
import com.blog.common.entity.SiteConfig;
import com.blog.common.exception.BusinessException;
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

    /** 与 site_config.config_key VARCHAR(100) 保持一致 */
    private static final int MAX_KEY_LENGTH = 100;

    /** config_value 为 TEXT（约 64KB），这里再收紧一档，避免异常大的内容入库 */
    private static final int MAX_VALUE_LENGTH = 20000;

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
        if (configs == null || configs.isEmpty()) {
            return Result.ok();
        }
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StrUtil.isBlank(key) || key.length() > MAX_KEY_LENGTH) {
                throw new BusinessException("配置项名称不合法: " + key);
            }
            if (value != null && value.length() > MAX_VALUE_LENGTH) {
                throw new BusinessException("配置项 " + key + " 内容过长（上限 " + MAX_VALUE_LENGTH + " 字）");
            }
            SiteConfig exist = siteConfigMapper.selectOne(
                    new LambdaQueryWrapper<SiteConfig>().eq(SiteConfig::getConfigKey, key));
            if (exist != null) {
                exist.setConfigValue(value);
                siteConfigMapper.updateById(exist);
            } else {
                SiteConfig c = new SiteConfig();
                c.setConfigKey(key);
                c.setConfigValue(value);
                siteConfigMapper.insert(c);
            }
        }
        return Result.ok();
    }
}
