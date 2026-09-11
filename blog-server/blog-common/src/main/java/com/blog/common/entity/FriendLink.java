package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("friend_link")
public class FriendLink extends BaseEntity {

    @NotBlank(message = "友链名称不能为空")
    @Size(max = 50, message = "友链名称不能超过 50 字")
    private String name;

    @NotBlank(message = "友链地址不能为空")
    @Size(max = 500, message = "友链地址不能超过 500 字")
    private String url;

    @Size(max = 500, message = "头像地址不能超过 500 字")
    private String avatar;

    @Size(max = 200, message = "描述不能超过 200 字")
    private String description;

    private Integer sort;

    private Integer status;
}
