package com.blog.common.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 按模型聚合的当日用量，用于模型列表里的「今日消耗」列。 */
@Data
public class AiUsageByModelVO {

    private String modelCode;
    private Integer requestCount;
    private Long tokens;
    private BigDecimal cost;
}
