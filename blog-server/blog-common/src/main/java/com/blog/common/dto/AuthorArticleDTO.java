package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 作者（author）发稿用的 DTO。
 *
 * <p>⚠️ 与 {@link ArticleDTO} 的关键差别：这里**没有** {@code status} / {@code isTop} /
 * {@code isFeatured} / {@code userId} / {@code publishAt} 这五个字段。
 *
 * <p>这不是"暂时不填"，是刻意留白的：作者提交的文章必须进待审队列，发布时间由超管审核
 * 通过的那一刻才写入，精品与置顶只有超管能给。
 *
 * <p>字段不存在 ⇒ Jackson 直接忽略请求体里的多余键 ⇒ 就算有人拿 Postman 手工塞
 * {@code {"status": 1}}，也进不了这个对象。这比"在 service 里写 if 判断"更硬一层：
 * 让错误的请求**在类型层面无法表达**。
 */
@Data
public class AuthorArticleDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    @Size(max = 500, message = "封面地址不能超过 500 字")
    private String cover;

    /**
     * 正文 Markdown。同 ArticleDTO：**故意不加 @NotBlank**，
     * 草稿允许只有标题；"提交审核必须有正文"由 service 按动作判断。
     */
    private String contentMd;

    private Long categoryId;

    private List<Long> tagIds;

    @Size(max = 200, message = "别名不能超过 200 字")
    private String slug;
}
