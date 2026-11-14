package com.blog.common.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 趋势折线上的一个点。
 *
 * <p>{@code groupKey} 而不是 modelCode：按厂商聚合时这一列装的是厂商名，
 * 沿用 modelCode 做字段名会误导调用方。
 */
@Data
public class AiUsageTrendVO {

    private LocalDate statDate;
    private String groupKey;
    private Integer requestCount;
    private Long tokens;
    private BigDecimal cost;
}
