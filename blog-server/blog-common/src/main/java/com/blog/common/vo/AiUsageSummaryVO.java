package com.blog.common.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 用量总量卡片：今日 / 近 N 天。字段名与 SQL 别名一一对应。 */
@Data
public class AiUsageSummaryVO {

    private Integer todayRequests;
    private Long todayTokens;
    private BigDecimal todayCost;

    private Integer totalRequests;
    private Long totalTokens;
    private BigDecimal totalCost;
}
