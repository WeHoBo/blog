package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("article")
public class Article extends BaseEntity {

    private String title;

    private String slug;

    private String contentMd;

    private String contentHtml;

    private String summary;

    private String cover;

    private Integer status;

    /** 定时发布时间：仅对草稿有意义，到期由定时任务自动转为已发布 */
    private LocalDateTime publishAt;

    private Long userId;

    private Long categoryId;

    private String series;

    private Integer isTop;

    private Integer viewCount;

    private Integer commentCount;

    private Integer likeCount;

    private Integer wordCount;

    private Integer isDeleted;
}
