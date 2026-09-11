package com.blog.common.vo;

import lombok.Data;

/**
 * 按分类统计「已发布文章数」的聚合结果。
 * 用于替代逐个分类 selectCount 的 N+1 查询。
 */
@Data
public class CategoryArticleCount {

    private Long categoryId;

    private Long cnt;
}
