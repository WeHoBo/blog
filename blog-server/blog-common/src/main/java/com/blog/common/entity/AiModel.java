package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * AI 模型注册表：一行 = 一个可选模型。
 *
 * <p>多厂商不靠代码分支，靠 {@code baseUrl + modelId} —— 国内主流厂商都是 OpenAI 兼容端点。
 *
 * <p>密钥两种模式（字段互斥）：
 * <ul>
 *   <li>{@code apiKeyCipher} 有值 → 后台填的密钥，AES-256-GCM 加密入库</li>
 *   <li>{@code apiKeyEnv} 有值 → 密钥留在服务器 .env，库里只存变量名</li>
 * </ul>
 *
 * ⚠️ {@code apiKeyCipher} 必须 {@code @JsonIgnore}：否则一个 GET 就把所有密文发出去了，
 * 密文配合泄漏的主密钥同样危险。前端只需要 {@code apiKeyMask}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model")
public class AiModel extends BaseEntity {

    private String displayName;

    private String provider;

    private String modelId;

    private String baseUrl;

    @JsonIgnore
    private String apiKeyCipher;

    /** 回显用掩码，如 sk-****3f2a */
    private String apiKeyMask;

    private String apiKeyEnv;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer contextWindow;

    private Integer supportStream;

    /** 0 表示不下发 tools 参数 —— 不支持 function calling 的厂商收到 tools 会直接 400 */
    private Integer supportTools;

    private BigDecimal inputPrice;

    private BigDecimal outputPrice;

    private Integer enabled;

    private Integer sort;

    private String remark;

    /** 全局配置 logic-delete-field=isDeleted，此字段存在即自动过滤已删除行 */
    private Integer isDeleted;
}
