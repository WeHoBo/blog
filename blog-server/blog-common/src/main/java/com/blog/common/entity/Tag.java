package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tag")
public class Tag extends BaseEntity {

    private String name;

    private String slug;

    /** 文章数量（非表字段，接口聚合返回） */
    @TableField(exist = false)
    private Integer articleCount;
}
