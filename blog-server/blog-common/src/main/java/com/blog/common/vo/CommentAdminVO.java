package com.blog.common.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端评论列表行。
 *
 * <p>比前台 CommentVO 多三样东西：文章标题（管理端要能看出是哪篇）、
 * 用户 id（便于封禁/追溯）、举报数（优先处理被举报的）。</p>
 */
@Data
public class CommentAdminVO {

    private Long id;

    private Long articleId;

    private String articleTitle;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String content;

    private Integer status;

    private LocalDateTime createTime;

    /** 该评论收到的举报总数 */
    private Integer reportCount;

    /** 其中待处理的举报数（>0 说明还需要人工看一眼） */
    private Integer pendingReportCount;
}
