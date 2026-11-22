package com.blog.common.dto;

import lombok.Data;

import java.util.Map;

/**
 * 提交申请答案：questionId -> 选项下标(0=A / 1=B / 2=C)。
 *
 * <p>用 Map 而不是 List，是因为前端按题目 id 取答案更自然，
 * 也天然兼容"某题没答"（key 缺失按答错处理）。
 */
@Data
public class ApplySubmitForm {
    private Map<Long, Integer> answers;
}
