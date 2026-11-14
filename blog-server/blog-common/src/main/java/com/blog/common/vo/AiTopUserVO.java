package com.blog.common.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Top 用户榜的一行。 */
@Data
public class AiTopUserVO {

    private Long userId;
    private Integer requestCount;
    private Long tokens;
    private BigDecimal cost;
    private LocalDateTime lastActive;
}
