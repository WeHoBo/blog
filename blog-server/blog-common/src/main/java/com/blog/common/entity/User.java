package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;

    private String password;

    /**
     * 用户自己设过密码吗。1 = 是；0 = 否。
     *
     * <p>⚠️ 这个字段不能省，也不能用「password 是否为空」代替：
     * OAuth 自动建号时写入的是 {@code BCrypt(随机 UUID)}，字段有值但没人知道原文。
     * 只能靠这个标记区分「真有可用密码」与「有密码字段但用不了」，
     * 个人中心据此提示"你还没设置密码"，改密码时据此决定要不要求旧密码。
     */
    private Integer passwordSet;

    /** 设置用户实际可用的密码。第三方账号的随机占位密码不走此方法。 */
    public void useEncodedPassword(String hash) {
        this.password = hash;
        this.passwordSet = 1;
    }

    private String nickname;

    private String avatar;

    /**
     * 个人简介。用户（含管理员）在「个人中心」自己写，展示在首页右栏作者卡片上。
     * 与站点设置里的 bloggerBio 是**两回事**：那个是站点广播（所有人看到同一份），
     * 这个是「我这个登录用户是谁」——所以访客看到站点广播，登录后看到自己的。
     */
    private String bio;

    private String email;

    /** 手机号（唯一，可为空）。OAuth 老用户没绑过，保持 NULL 不影响唯一索引 */
    private String phone;

    /** 手机号最近一次通过短信验证的时间 */
    private LocalDateTime phoneVerifiedAt;

    private String role;

    private Integer status;

    private String githubId;

    private String giteeId;

    private String huaweiId;

    private String source;
}
