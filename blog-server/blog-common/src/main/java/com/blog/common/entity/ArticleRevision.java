package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章版本快照。
 * <p>
 * 每次保存（更新）前，把「即将被覆盖的那一版」写进来，因此它保存的是历史而非当前。
 * 故意不继承 {@link BaseEntity}：快照只需要一个创建时间，没有 update_time 语义，
 * 也不参与逻辑删除。
 */
@Data
@TableName("article_revision")
public class ArticleRevision implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private String title;

    private String slug;

    private String contentMd;

    private String summary;

    private String cover;

    private Long categoryId;

    private String series;

    private Integer status;

    private Integer wordCount;

    /** 本次修改人（做这次保存的 admin，而不是文章原作者） */
    private Long editorId;

    private LocalDateTime createTime;
}
