package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("friend_link")
public class FriendLink extends BaseEntity {

    private String name;

    private String url;

    private String avatar;

    private String description;

    private Integer sort;

    private Integer status;
}
