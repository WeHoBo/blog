package com.blog.common.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 管理端举报列表行（已经把评论正文、文章标题、举报人都拼好，前端不用再查） */
@Data
public class CommentReportVO {

    private Long id;

    private Long commentId;

    /** 被举报评论的正文；评论已被物理删除时为 null */
    private String commentContent;

    private Integer commentStatus;

    private Long articleId;

    private String articleTitle;

    private Long reporterId;

    private String reporterNickname;

    private String reason;

    /** 0 待处理 / 1 已忽略 / 2 已删除该评论 */
    private Integer status;

    private LocalDateTime createTime;
}
