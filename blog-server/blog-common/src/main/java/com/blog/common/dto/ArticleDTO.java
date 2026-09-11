package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过 200 字")
    private String title;

    @Size(max = 500, message = "摘要不能超过 500 字")
    private String summary;

    @Size(max = 500, message = "封面地址不能超过 500 字")
    private String cover;

    /**
     * 正文 Markdown。
     * <p>
     * 这里**故意不加 @NotBlank**：草稿允许只有标题（写一半先存下来是最基本的需求）。
     * 「发布时必须有正文」的约束由 ArticleService 按 status 判断，
     * 这样既能存空草稿，又不会把空文章发出去。
     */
    private String contentMd;

    /**
     * 保留字段：正文 HTML 目前无人写入也无人读取（前端恒传空串，渲染统一走 contentMd）。
     * 为兼容旧客户端保留在此，不再参与业务。
     */
    private String contentHtml;

    private Long categoryId;

    @Size(max = 100, message = "系列名不能超过 100 字")
    private String series;

    @Size(max = 200, message = "别名不能超过 200 字")
    private String slug;

    private List<Long> tagIds;

    /** 0 草稿 / 1 已发布 */
    private Integer status;

    private Integer isTop;

    /** 定时发布时间；仅当 status=0 且时间在未来时生效 */
    private LocalDateTime publishAt;
}
