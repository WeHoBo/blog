package com.blog.common.vo;

import com.blog.common.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {
    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    /** 个人简介，用户在个人中心自己写 */
    private String bio;
    private String role;
    /** 手机号（仅回给本人；未绑定为 null） */
    private String phone;
    /**
     * 是否已设置过可用密码。
     *
     * <p>为 false 时前端显示"你还没设置密码" —— 这类账号是 OAuth 自动建的，
     * 库里的 password 只是 BCrypt(随机 UUID)，用户不可能知道原文。
     */
    private Boolean passwordSet;

    /**
     * 由实体统一构造，避免各处手写参数时漏字段或顺序错位。
     * 登录接口传 token，{@code /auth/me} 传 null。
     */
    public static LoginVO of(User user, String token) {
        if (user == null) {
            return null;
        }
        return new LoginVO(
                token,
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getAvatar(),
                user.getBio(),
                user.getRole(),
                user.getPhone(),
                user.getPasswordSet() != null && user.getPasswordSet() == 1
        );
    }
}
