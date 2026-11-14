package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 执行轨迹：一轮问答的完整步骤链。
 *
 * P-1 阶段不写这张表（还没有工具循环），P0 接入 Agent 后每轮写入一条。
 * 提前建好是为了让 P0 不必再动一次数据库。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_trace")
public class AgentTrace extends BaseEntity {

    private Long userId;

    private String question;

    /** 实际使用的模型标识 */
    private String model;

    /** 步骤链 JSON：[{"step":1,"tool":"search_articles","ms":83,"ok":true}] */
    private String steps;

    private Integer stepCount;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalMs;

    /** ok | timeout | error | aborted */
    private String status;

    private String errorMsg;
}
