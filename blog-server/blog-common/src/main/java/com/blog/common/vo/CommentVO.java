package com.blog.common.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {
    private Long id;
    private Long articleId;
    private Long parentId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
}
