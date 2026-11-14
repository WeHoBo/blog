package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 每日用量聚合（按 日期 + 用户 + 模型）。
 *
 * 明细不在这里：这里只存预聚合值，后台图表直接查这张表，不扫 agent_trace。
 * cost 在写入时按当时单价算好并落库 —— 若改成查询时联表乘单价，
 * 日后调整模型价格会把历史账单一起改掉。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_usage_daily")
public class AiUsageDaily extends BaseEntity {

    /** 统计日期（服务器时区 +08:00） */
    private LocalDate statDate;

    private Long userId;

    /** 模型标识，如 deepseek-chat；与 P2 的 ai_model.model_id 对应 */
    private String modelCode;

    /** 提问次数（按轮计，一次提问 = 1，不按 Agent 内部步数计） */
    private Integer requestCount;

    private Long promptTokens;

    private Long completionTokens;

    /** 估算金额（元），写入时快照，不随后续调价变化 */
    private BigDecimal cost;
}
