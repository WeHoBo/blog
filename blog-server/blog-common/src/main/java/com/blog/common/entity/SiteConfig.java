package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("site_config")
public class SiteConfig extends BaseEntity {

    private String configKey;

    private String configValue;

    private String description;
}
