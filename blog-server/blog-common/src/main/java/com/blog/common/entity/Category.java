package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名不能超过 50 字")
    private String name;

    @Size(max = 100, message = "分类别名不能超过 100 字")
    private String slug;

    private Long parentId;

    private Integer sort;

    private Integer articleCount;
}
