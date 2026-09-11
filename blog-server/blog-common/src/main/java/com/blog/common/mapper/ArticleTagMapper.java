package com.blog.common.mapper;

import com.blog.common.entity.ArticleTag;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.vo.TagArticleCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    /**
     * 一条 SQL 统计各标签下已发布文章数，避免把 article / article_tag 全量载入内存。
     */
    @Select("SELECT at.tag_id AS tag_id, COUNT(*) AS cnt FROM article_tag at "
            + "JOIN article a ON a.id = at.article_id "
            + "WHERE a.status = 1 AND a.is_deleted = 0 "
            + "GROUP BY at.tag_id")
    List<TagArticleCount> countPublishedByTag();
}
