package com.blog.common.dto;

import lombok.Data;

/** 提交举报的请求体 */
@Data
public class CommentReportForm {

    private Long commentId;

    /** 举报原因，必须是 CommentReportReasons 里的固定值 */
    private String reason;
}
