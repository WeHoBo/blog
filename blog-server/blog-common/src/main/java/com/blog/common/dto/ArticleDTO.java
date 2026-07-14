package com.blog.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ArticleDTO {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String summary;

    private String cover;

    @NotBlank(message = "内容不能为空")
    private String contentMd;

    private String contentHtml;

    private Long categoryId;

    private List<Long> tagIds;

    private Integer status;

    private Integer isTop;
}
