package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tag")
public class Tag extends BaseEntity {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 50, message = "标签名不能超过 50 字")
    private String name;

    @Size(max = 100, message = "标签别名不能超过 100 字")
    private String slug;

    /** 文章数量（非表字段，接口聚合返回） */
    @TableField(exist = false)
    private Integer articleCount;
}
