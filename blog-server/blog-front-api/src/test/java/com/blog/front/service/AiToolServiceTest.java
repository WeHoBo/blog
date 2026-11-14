package com.blog.front.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工具网关的契约测试。
 *
 * 这里锁的是三件容易出大事的事：
 *   1. 模型读文章**不能**顺手把阅读数 +1（会污染站点统计）
 *   2. 工具失败必须转成 ok=false 而不是抛异常（抛了整轮对话就没了）
 *   3. 名称找不到时要给出可重试的提示，而不是静默返回空列表
 */
class AiToolServiceTest {

    private ArticleService articleService;
    private AiToolService service;

    @BeforeEach
    void setUp() {
        articleService = mock(ArticleService.class);
        service = new AiToolService(articleService);
    }

    /** 未知工具名：不抛异常，返回可读错误 —— 防止模型瞎编一个工具名就把整轮搞崩 */
    @Test
    void unknownTool_returnsErrorInsteadOfThrowing() {
        Map<String, Object> out = service.execute("drop_database", Map.of());

        assertEquals(false, out.get("ok"));
        assertTrue(String.valueOf(out.get("error")).contains("未知工具"));
    }

    /** q 为空：直接拒，不去查库 */
    @Test
    void searchArticles_blankQuery_rejected() {
        Map<String, Object> out = service.execute("search_articles", Map.of("q", "   "));

        assertEquals(false, out.get("ok"));
        verify(articleService, never()).page(anyInt(), anyInt(), any(), any(), anyString(), any(), any());
    }

    /** limit 超过 8 要夹到 8，否则模型传 limit=100 会把上下文顶爆 */
    @Test
    void searchArticles_clampsLimitToMax() {
        Page<Article> page = new Page<>(1, 8);
        page.setRecords(List.of(article(1L, "RAG 入门", "rag-intro")));
        page.setTotal(1);
        when(articleService.page(eq(1), eq(8), isNull(), isNull(), eq("RAG"), isNull(), isNull()))
                .thenReturn(page);
        when(articleService.tagNamesByArticleIds(any())).thenReturn(Map.of());
        when(articleService.listCategories()).thenReturn(List.of());

        Map<String, Object> out = service.execute("search_articles", Map.of("q", "RAG", "limit", 100));

        assertEquals(true, out.get("ok"));
        verify(articleService).page(1, 8, null, null, "RAG", null, null);
    }

    /** 分类名写错时不能静默返回空 —— 要提示模型先查 list_taxonomy，否则它会以为站内没有相关内容 */
    @Test
    void searchArticles_unknownCategory_hintsToUseTaxonomy() {
        when(articleService.listCategories()).thenReturn(List.of(category(1L, "Java")));

        Map<String, Object> out = service.execute("search_articles",
                Map.of("q", "线程", "category", "不存在的分类"));

        assertEquals(true, out.get("ok"));
        assertTrue(String.valueOf(out.get("digest")).contains("list_taxonomy"));
        verify(articleService, never()).page(anyInt(), anyInt(), any(), any(), any(), any(), any());
    }

    /** ⭐ 模型读文章必须 countView=false，否则每问一次阅读数就涨一次 */
    @Test
    void getArticle_neverIncrementsViewCount() {
        Article a = article(7L, "手写 RAG", "hand-rag");
        a.setContentMd("正文内容");
        a.setWordCount(1200);
        when(articleService.getPublicById(7L, false)).thenReturn(a);
        when(articleService.listCategories()).thenReturn(List.of());
        when(articleService.getTagsByArticleId(7L)).thenReturn(List.of());

        Map<String, Object> out = service.execute("get_article", Map.of("id", 7));

        assertEquals(true, out.get("ok"));
        verify(articleService).getPublicById(7L, false);
    }

    /** 超长正文要截断，并把 truncated 标记回给模型，避免它以为文章就这么多 */
    @Test
    void getArticle_truncatesLongContent() {
        Article a = article(8L, "长文", "long");
        a.setContentMd("x".repeat(9000));
        a.setWordCount(9000);
        when(articleService.getPublicById(8L, false)).thenReturn(a);
        when(articleService.listCategories()).thenReturn(List.of());
        when(articleService.getTagsByArticleId(8L)).thenReturn(List.of());

        Map<String, Object> out = service.execute("get_article", Map.of("id", 8));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) out.get("data");
        assertEquals(true, data.get("truncated"));
        assertTrue(String.valueOf(data.get("content")).length() < 9000);
    }

    /** id 与 slug 都没给：报错但可重试 */
    @Test
    void getArticle_withoutIdOrSlug_rejected() {
        Map<String, Object> out = service.execute("get_article", Map.of());

        assertEquals(false, out.get("ok"));
    }

    /** 业务异常（文章不存在）要被翻译成 ok=false，不能冒泡成 500 */
    @Test
    void getArticle_notFound_becomesErrorResult() {
        when(articleService.getPublicById(99L, false))
                .thenThrow(new BusinessException(404, "文章不存在"));

        Map<String, Object> out = service.execute("get_article", Map.of("id", 99));

        assertEquals(false, out.get("ok"));
        assertTrue(String.valueOf(out.get("error")).contains("文章不存在"));
    }

    /** 未预期异常（比如空指针）也要兜住，只回类名不泄内部细节 */
    @Test
    void unexpectedException_isSwallowed() {
        when(articleService.listCategories()).thenThrow(new IllegalStateException("db down"));

        Map<String, Object> out = service.execute("list_taxonomy", Map.of("type", "category"));

        assertEquals(false, out.get("ok"));
        assertNotNull(out.get("error"));
    }

    /** site_stats 要真的去查数，不能返回占位值 */
    @Test
    void siteStats_reportsRealNumbers() {
        Page<Article> page = new Page<>(1, 1);
        page.setRecords(List.of(article(1L, "最新", "newest")));
        page.setTotal(42);
        when(articleService.page(1, 1, null, null, null, null, null)).thenReturn(page);
        when(articleService.listCategories()).thenReturn(List.of(category(1L, "Java"), category(2L, "AI")));
        when(articleService.listTags()).thenReturn(List.of());

        Map<String, Object> out = service.execute("site_stats", Map.of());

        assertEquals(true, out.get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) out.get("data");
        assertEquals(42L, data.get("articles"));
        assertEquals(2, data.get("categories"));
        assertEquals(0, data.get("tags"));
    }

    // ---------------- 构造辅助 ----------------

    private Article article(Long id, String title, String slug) {
        Article a = new Article();
        a.setId(id);
        a.setTitle(title);
        a.setSlug(slug);
        a.setSummary("摘要");
        a.setStatus(1);
        a.setIsDeleted(0);
        return a;
    }

    private Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }
}
