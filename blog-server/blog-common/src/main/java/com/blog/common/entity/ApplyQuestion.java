package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「申请发布文章」的一道选择题。
 *
 * <p>🔴 {@link #answer} 是正确答案的下标（0/1/2），**绝不能下发给前端**。
 * 出题接口只返回题干与三个选项，判分在服务端做 —— 前端拿不到答案，也就无法
 * 直接从网络面板里读出通关密码。这也是为什么它不能塞进 {@code site_config}：
 * 那张表会被 {@code /api/site-config/public} 整表公开。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("apply_question")
public class ApplyQuestion extends BaseEntity {

    /** 题干 */
    private String question;

    private String optionA;

    private String optionB;

    private String optionC;

    /** 正确选项下标：0=A / 1=B / 2=C */
    private Integer answer;

    /** 展示顺序，小的在前 */
    private Integer sort;

    /** 1=启用，0=停用。停用的题不出现在申请弹窗里，但保留配置 */
    private Integer enabled;
}
