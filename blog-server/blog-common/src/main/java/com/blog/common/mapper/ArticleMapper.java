package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.Article;
import com.blog.common.vo.CategoryArticleCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 一条 SQL 统计各分类「直属」的已发布文章数，替代逐个分类 selectCount 的 N+1 查询。
     * 子孙分类的汇总在应用层做一次记忆化递归即可（分类数量很小）。
     */
    @Select("SELECT category_id AS category_id, COUNT(*) AS cnt FROM article "
            + "WHERE status = 1 AND is_deleted = 0 AND category_id IS NOT NULL "
            + "GROUP BY category_id")
    List<CategoryArticleCount> countPublishedByCategory();
}
