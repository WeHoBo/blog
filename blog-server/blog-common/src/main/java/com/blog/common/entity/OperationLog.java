package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {
    private Long userId;
    private String action;
    private Long targetId;
    private String targetType;

    /**
     * 1 = 成功，0 = 失败。
     *
     * <p>⚠️ 这一列是本次扩容的重点：以前只有「成功了才写一行」，
     * 于是审计表永远只有成功记录，看不出「谁试过但被拒绝了」。
     * 审核制下最该查的恰恰是失败/越权的那几次。
     */
    private Integer status;

    /** 该操作耗时（毫秒）。用于事后区分「慢」是数据库还是业务逻辑 */
    private Long costMs;

    /** 客户端 IP（完整存，仅后台可见，安全排查需要原值） */
    private String ip;

    /**
     * 与文件日志的 requestId 对齐。
     *
     * <p>有了它才能从"后台看到一条失败审计"直接跳到"blog-api.log 里那次请求的全部日志"。
     * 长度按 UUID 预留 36。
     */
    private String requestId;
}
