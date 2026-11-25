package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("article")
public class Article extends BaseEntity {

    private String title;

    @TableField(exist = false)
    private String editVersion;

    private String slug;

    private String contentMd;

    private String contentHtml;

    private String summary;

    private String cover;

    /**
     * 状态：0 草稿 / 1 已发布 / 2 待审 / 3 已驳回。
     *
     * <p>⚠️ 只有 {@code 1} 是公开的。全站所有公开查询（列表、详情、归档、系列、sitemap、
     * 相关推荐、AI 检索）都写死 {@code status = 1}，所以 2/3 天然对访客隐身，
     * 不需要逐个出口去补过滤条件 —— 新增状态值之所以安全，正是因为这条口径早就统一了。
     *
     * <p>⚠️ **待审不能用 0**：{@code ScheduledPublishJob} 每分钟会把
     * 「status=0 且 publish_at 已到期」的草稿自动翻成已发布。若待审复用 0，
     * 作者只要把时间调到过去，一分钟后就绕过审核上线了。
     */
    private Integer status;

    /** 驳回理由（仅 status=3 时有值），让作者知道该改什么，别反复重交同一篇。 */
    private String rejectReason;

    /**
     * 发布时间。
     * <p>⚠️ 它**不再只是草稿的定时字段**：{@code status=1} 时保留用户选的时间，
     * 定时发布也不会清空。列表卡片的日期口径是 {@code COALESCE(publish_at, create_time)}，
     * 所以它为空时才回落到创建时间。
     */
    private LocalDateTime publishAt;

    private Long userId;

    private Long categoryId;

    private String series;

    private Integer isTop;

    /**
     * 是否标记为「精品」。
     * <p>⚠️ 它**不决定首页布局**：首页中间那栏由 {@link #isTop}（置顶）驱动，
     * 置顶文章也不会再出现在右侧「全部文章」里。精品只是一个由超管手工授予的
     * 质量徽标，在列表卡片上显示，用于给读者一个「这篇值得读」的提示。
     * <p>与 {@link #isTop} 刻意分开：isTop 是**排位**概念（参与全站 is_top DESC 排序
     * 与首页中间栏），isFeatured 是**标记**概念（不改排序、不改归属）。
     * <p>只有超级管理员能把它置 1，普通管理员保存时由服务端丢弃。
     */
    private Integer isFeatured;

    private Integer viewCount;

    private Integer commentCount;

    private Integer likeCount;

    private Integer wordCount;

    private Integer isDeleted;
}
