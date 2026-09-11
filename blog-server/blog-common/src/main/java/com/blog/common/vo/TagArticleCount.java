package com.blog.common.vo;

import lombok.Data;

/**
 * 按标签统计「已发布文章数」的聚合结果。
 * 用于替代把 article / article_tag 全量载入内存再分组的做法。
 */
@Data
public class TagArticleCount {

    private Long tagId;

    private Long cnt;
}
