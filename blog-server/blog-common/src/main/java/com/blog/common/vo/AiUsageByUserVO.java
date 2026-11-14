package com.blog.common.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 树状图的一格：某用户在某模型上的消耗。 */
@Data
public class AiUsageByUserVO {

    private Long userId;
    private String modelCode;
    private Integer requestCount;
    private BigDecimal cost;
}
