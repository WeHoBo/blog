package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SiteConfigMapper extends BaseMapper<SiteConfig> {
    @Insert("INSERT INTO site_config (config_key, config_value) VALUES (#{key}, #{value}) " +
            "ON DUPLICATE KEY UPDATE config_value = #{value}")
    void upsert(@Param("key") String key, @Param("value") String value);
}
