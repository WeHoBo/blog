package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 全局设置：固定单行 id=1。
 *
 * <p>默认模型用**单值** {@code defaultModelId} 表达，不用 {@code is_default} 布尔位 ——
 * 布尔位在并发下很容易出现两行都为 1，之后谁都说不清哪个才是默认。
 *
 * <p>不继承 BaseEntity：主键是手工指定的固定行（IdType.INPUT），不是自增。
 */
@Data
@TableName("ai_setting")
public class AiSetting {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long defaultModelId;

    private Long fallbackModelId;

    private Integer allowUserChoice;

    private Integer minuteLimit;

    private Integer hourlyLimit;

    private Integer dailyLimit;

    private Integer dailyTokenLimit;

    private BigDecimal globalDailyBudget;

    /** 每次改动 +1，供运行时缓存失效 */
    private Long configVersion;

    private Long updatedBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
