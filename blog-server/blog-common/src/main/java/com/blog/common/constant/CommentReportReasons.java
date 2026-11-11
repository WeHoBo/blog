package com.blog.common.constant;

import java.util.Set;

/**
 * 评论举报原因（固定枚举）。
 *
 * <p>为什么不做自由文本：前台只给几个选项，管理员一眼就能分类处理；
 * 自由文本会变成第二个「评论框」，既难审核也容易被塞广告。</p>
 */
public final class CommentReportReasons {

    public static final String SPAM = "垃圾广告";
    public static final String ABUSE = "辱骂攻击";
    public static final String ILLEGAL = "违法违规";
    public static final String OTHER = "其他";

    public static final Set<String> ALL = Set.of(SPAM, ABUSE, ILLEGAL, OTHER);

    private CommentReportReasons() {
    }
}
