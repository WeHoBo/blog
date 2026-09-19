package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.Article;
import com.blog.common.vo.CategoryArticleCount;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 把「还没有分类」的文章统一归属到默认分类（「未建档文章」）。
     * 走原生 UPDATE，不受 MyBatis-Plus 逻辑删除插件影响 —— 回收站里的历史文章也一并补齐，
     * 这样从回收站恢复出来的文章同样带着分类。幂等：第一次执行后就没有 category_id IS NULL 的行了。
     */
    @Update("UPDATE article SET category_id = #{categoryId} WHERE category_id IS NULL")
    int assignNullCategory(@Param("categoryId") Long categoryId);

    /**
     * 级联删除分类时调用：把被删分类（含其子孙）下的文章统一改挂到默认分类。
     * 走原生 SQL 以覆盖回收站里的文章；不采用「先置 NULL、等下次启动回填」，
     * 因为分类页文章数按 category_id 聚合，留 NULL 会让这批文章在「未建档文章」里数不到。
     */
    @Update("<script>UPDATE article SET category_id = #{categoryId} WHERE category_id IN "
            + "<foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>#{cid}</foreach>"
            + "</script>")
    int reassignCategories(@Param("categoryIds") List<Long> categoryIds, @Param("categoryId") Long categoryId);

    // ---------------------------------------------------------------
    // 以下方法用于「回收站」。逻辑删除由 MyBatis-Plus 在自动生成的 SQL 上注入
    // is_deleted = 0，因此常规 BaseMapper 方法根本查不到已删除的行，
    // 这里用原生 SQL 显式绕过（项目约定零 XML，故全部走注解）。
    // ---------------------------------------------------------------

    /** 回收站分页（只取展示字段，不读 content_md / content_html） */
    @Select("<script>"
            + "SELECT id, title, slug, status, user_id, category_id, series, is_top, "
            + "view_count, comment_count, like_count, word_count, create_time, update_time "
            + "FROM article WHERE is_deleted = 1 "
            + "<if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "ORDER BY update_time DESC LIMIT #{offset}, #{size}"
            + "</script>")
    List<Article> selectTrashPage(@Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    /** 回收站总数 */
    @Select("<script>"
            + "SELECT COUNT(*) FROM article WHERE is_deleted = 1 "
            + "<if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%') </if>"
            + "</script>")
    long countTrash(@Param("keyword") String keyword);

    /** 从回收站恢复 */
    @Update("UPDATE article SET is_deleted = 0 WHERE id = #{id} AND is_deleted = 1")
    int restoreById(@Param("id") Long id);

    /** 彻底删除（物理删除，绕过逻辑删除） */
    @Delete("DELETE FROM article WHERE id = #{id}")
    int forceDeleteById(@Param("id") Long id);

    /**
     * slug 占用检查。
     * 注意：逻辑删除的行仍然占着 uk_slug 唯一索引，所以这里**不过滤 is_deleted**，
     * 否则会给出「可用」的假信号，等真正插入时才报唯一键冲突。
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM article WHERE slug = #{slug} "
            + "<if test='excludeId != null'> AND id &lt;&gt; #{excludeId} </if>"
            + "</script>")
    long countBySlugIncludingDeleted(@Param("slug") String slug, @Param("excludeId") Long excludeId);
}
