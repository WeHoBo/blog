package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.ArticleRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

import java.time.LocalDateTime;

@Mapper
public interface ArticleRevisionMapper extends BaseMapper<ArticleRevision> {

    /**
     * 版本列表：**不返回 content_md**。
     * 一个改过几十次的文章会有几十份全文快照，列表只需要元信息，
     * 正文等用户点开某一版时再按 id 单查（与文章列表排除 LONGTEXT 是同一个理由）。
     */
    @Select("SELECT id, article_id, title, status, word_count, editor_id, create_time "
            + "FROM article_revision WHERE article_id = #{articleId} "
            + "ORDER BY create_time DESC, id DESC LIMIT #{limit}")
    List<ArticleRevision> selectMetaList(@Param("articleId") Long articleId,
                                         @Param("limit") int limit);

    /** 最近一次快照时间，用于「自动保存不留太多版本」的节流判断 */
    @Select("SELECT MAX(create_time) FROM article_revision WHERE article_id = #{articleId}")
    LocalDateTime selectLatestTime(@Param("articleId") Long articleId);
}
