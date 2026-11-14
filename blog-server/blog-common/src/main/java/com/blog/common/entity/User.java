package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;

    private String password;

    private String nickname;

    private String avatar;

    /**
     * 个人简介。用户（含管理员）在「个人中心」自己写，展示在首页右栏作者卡片上。
     * 与站点设置里的 bloggerBio 是**两回事**：那个是站点广播（所有人看到同一份），
     * 这个是「我这个登录用户是谁」——所以访客看到站点广播，登录后看到自己的。
     */
    private String bio;

    private String email;

    private String role;

    private Integer status;

    private String githubId;

    private String giteeId;

    private String huaweiId;

    private String source;
}
