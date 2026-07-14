package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

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

    private Long userId;

    private Long categoryId;

    private Integer isTop;

    private Integer viewCount;

    private Integer commentCount;

    private Integer likeCount;

    private Integer isDeleted;
}
