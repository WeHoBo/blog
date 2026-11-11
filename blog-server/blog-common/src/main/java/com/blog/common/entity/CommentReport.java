package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 评论举报。
 *
 * <p>只存「谁举报了哪条评论、原因是什么、处理到哪一步」，
 * 不复制评论正文 —— 评论被删后举报记录仍在（status = 2），方便事后追溯。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("comment_report")
public class CommentReport extends BaseEntity {

    /** 被举报评论 id */
    private Long commentId;

    /** 举报人用户 id */
    private Long userId;

    /** 举报原因（固定枚举，见 CommentReportReasons） */
    private String reason;

    /** 0 待处理 / 1 已忽略 / 2 已删除该评论 */
    private Integer status;
}
