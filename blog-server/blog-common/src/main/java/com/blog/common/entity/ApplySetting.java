package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「申请成为作者」的全局设置（单行表，id 恒为 1）。
 *
 * <p>为什么单独开一张表而不是塞进 {@code site_config}：那张表的公开接口会返回全表，
 * 而这张表里虽然没有答案，却有 {@code enabled} 这种"后台开关"，
 * 混进公开配置里迟早会被人当成可读字段依赖上。单开一张，边界更清楚。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("apply_setting")
public class ApplySetting extends BaseEntity {

    /** 答对多少题算通过。保存时会 clamp 到 [1, 启用题目数] */
    private Integer passCount;

    /** 1=开放申请，0=关闭（首页入口仍显示，但点开会提示暂未开放） */
    private Integer enabled;
}
