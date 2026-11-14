package com.blog.front.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.entity.Article;
import com.blog.common.entity.ArticleRevision;
import com.blog.common.entity.ArticleTag;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleRevisionMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
    private final ArticleRevisionMapper articleRevisionMapper = mock(ArticleRevisionMapper.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    /**
     * LambdaQueryWrapper.select(方法引用...) 会立即解析列名，依赖 MyBatis-Plus 的
     * TableInfo 缓存。这里全部 mapper 都是 mock，没有 Spring/MyBatis 启动过程，
     * 缓存为空会抛 "can not find lambda cache for this entity"，故手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> entity : List.of(Article.class, ArticleRevision.class, ArticleTag.class,
                Tag.class, Category.class)) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }

    private ArticleService service() {
        return new ArticleService(articleMapper, categoryMapper, tagMapper,
                articleTagMapper, articleRevisionMapper, redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void mockCacheMiss() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private Article article(Long id, int status) {
        Article article = new Article();
        article.setId(id);
        article.setStatus(status);
        return article;
    }

    // ---------------- 列表 / 详情（既有行为） ----------------

    @Test
    void page_timeSort_defaultsToCreateTimeDesc() {
        ArticleService s = service();
        assertDoesNotThrow(() -> s.page(1, 10, null, null, null, null, "time"));
    }

    @Test
    void page_titleSort_doesNotThrow() {
        ArticleService s = service();
        assertDoesNotThrow(() -> s.page(1, 10, null, null, null, null, "title"));
    }

    @Test
    void page_withCategoryAndKeyword_doesNotThrow() {
        ArticleService s = service();
        assertDoesNotThrow(() -> s.page(1, 10, 3L, null, "Spring", null, "title"));
    }

    @Test
    void page_withTag_doesNotThrow() {
        ArticleService s = service();
        assertDoesNotThrow(() -> s.page(1, 10, null, 5L, null, null, "time"));
    }

    @Test
    void getPublicById_throwsWhenMissing() {
        mockCacheMiss();
        ArticleService s = service();
        assertThrows(BusinessException.class, () -> s.getPublicById(999L));
    }

    @Test
    void getPublicById_throwsWhenDraft() {
        mockCacheMiss();
        when(articleMapper.selectById(1L)).thenReturn(article(1L, 0));
        ArticleService s = service();
        assertThrows(BusinessException.class, () -> s.getPublicById(1L));
    }

    // ---------------- 阅读数：服务端渲染与浏览器打点 ----------------

    @Test
    void getPublicById_ssrRequest_doesNotCountView() {
        mockCacheMiss();
        Article published = article(1L, 1);
        published.setViewCount(10);
        when(articleMapper.selectById(1L)).thenReturn(published);
        ArticleService s = service();

        Article out = s.getPublicById(1L, false);

        assertEquals(10, out.getViewCount(), "SSR 渲染不应把阅读数算进去");
        verify(articleMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void getPublicById_browserRequest_countsView() {
        mockCacheMiss();
        // 真实 Redis 的 increment 返回自增后的值（从 1 开始），这里如实模拟
        when(redisTemplate.opsForValue().increment(anyString())).thenReturn(1L);
        Article published = article(1L, 1);
        published.setViewCount(10);
        when(articleMapper.selectById(1L)).thenReturn(published);
        ArticleService s = service();

        Article out = s.getPublicById(1L, true);

        assertEquals(11, out.getViewCount(), "基准 10 + 本次增量 1");
        verify(articleMapper).update(any(), any());
    }

    @Test
    void incrementViewCount_returnsLatestCount() {
        mockCacheMiss();
        when(redisTemplate.opsForValue().increment(anyString())).thenReturn(1L);
        Article published = article(2L, 1);
        published.setViewCount(null);
        when(articleMapper.selectById(2L)).thenReturn(published);
        ArticleService s = service();

        assertEquals(1, s.incrementViewCount(2L), "阅读数为空时应从 0 开始算");
        verify(articleMapper).update(any(), any());
    }

    @Test
    void incrementViewCount_draft_throws404() {
        when(articleMapper.selectById(3L)).thenReturn(article(3L, 0));
        ArticleService s = service();
        BusinessException ex = assertThrows(BusinessException.class, () -> s.incrementViewCount(3L));
        assertEquals(404, ex.getCode());
        verify(articleMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void getAdminById_returnsDraft() {
        Article draft = article(1L, 0);
        when(articleMapper.selectById(1L)).thenReturn(draft);
        ArticleService s = service();
        assertEquals(draft, s.getAdminById(1L));
    }

    @SuppressWarnings("unchecked")
    @Test
    void page_selectsOnlyListColumns_excludesArticleBody() {
        ArticleService s = service();
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(articleMapper.selectPage(any(), captor.capture())).thenReturn(new Page<>());

        s.page(1, 10, null, null, null, null, "time");

        String select = captor.getValue().getSqlSelect();
        assertNotNull(select, "列表查询应显式指定返回列");
        assertTrue(select.contains("title"));
        assertTrue(select.contains("word_count"));
        assertFalse(select.contains("content_md"), "列表不应读取正文 Markdown");
        assertFalse(select.contains("content_html"), "列表不应读取正文 HTML");
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminPage_selectsOnlyListColumns_excludesBodyAndPassword() {        ArticleService s = service();
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(articleMapper.selectPage(any(), captor.capture())).thenReturn(new Page<>());

        s.adminPage(1, 10, null, null, null);

        String select = captor.getValue().getSqlSelect();
        assertNotNull(select, "后台列表查询应显式指定返回列");
        assertFalse(select.contains("content_md"));
        assertFalse(select.contains("content_html"));
        assertFalse(select.contains("password"), "后台列表不应下发密码字段");
    }

    // ---------------- 关键词搜索范围 ----------------

    @SuppressWarnings("unchecked")
    @Test
    void page_keyword_matchesTitleSummaryTagAndCategory() {
        // 标签名命中「Java」→ 关联文章 11、12
        Tag javaTag = new Tag();
        javaTag.setId(7L);
        javaTag.setName("Java");
        when(tagMapper.selectList(any())).thenReturn(List.of(javaTag));
        ArticleTag link1 = new ArticleTag();
        link1.setArticleId(11L);
        link1.setTagId(7L);
        ArticleTag link2 = new ArticleTag();
        link2.setArticleId(12L);
        link2.setTagId(7L);
        when(articleTagMapper.selectList(any())).thenReturn(List.of(link1, link2));
        // 分类名命中「Java」→ 分类 3
        Category javaCategory = new Category();
        javaCategory.setId(3L);
        javaCategory.setName("Java");
        when(categoryMapper.selectList(any())).thenReturn(List.of(javaCategory));

        ArticleService s = service();
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(articleMapper.selectPage(any(), captor.capture())).thenReturn(new Page<>());

        s.page(1, 10, null, null, "Java", null, "time");

        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("title"), "标题仍应参与匹配");
        assertTrue(sql.contains("summary"), "摘要也应参与匹配（正文太长，不进 LIKE）");
        assertTrue(sql.contains("category_id"), "分类名命中应并入查询");
        assertTrue(sql.contains("OR id IN"), "标签名命中应并入查询");
    }

    @SuppressWarnings("unchecked")
    @Test
    void page_keyword_keepsStatusAndDeletedOutsideOrGroup() {
        ArticleService s = service();
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(articleMapper.selectPage(any(), captor.capture())).thenReturn(new Page<>());

        s.page(1, 10, null, null, "Spring", null, "time");

        // 标签 / 分类都没命中时，关键词只剩标题 + 摘要
        String sql = captor.getValue().getSqlSegment();
        assertFalse(sql.contains("category_id"), "没有分类命中时不应拼 category_id 条件");
        assertTrue(sql.contains("summary"));

        // 关键结构：status / is_deleted 必须在关键词分组之前。
        // 若把 OR 平铺在外层，SQL 会变成 status=1 AND is_deleted=0 AND title LIKE ? OR ...，
        // 草稿与已删除文章会直接漏进前台搜索结果。
        int groupStart = sql.indexOf("AND (");
        assertTrue(groupStart > 0, "关键词条件必须用 AND (...) 包成一组");
        assertTrue(sql.indexOf("status") < groupStart, "status 必须留在关键词分组之外");
        assertTrue(sql.indexOf("is_deleted") < groupStart, "is_deleted 必须留在关键词分组之外");
    }

    // ---------------- 空草稿 & 发布校验 ----------------

    @Test
    void create_draftWithoutContent_isAllowed() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("只写了一半的草稿");
        dto.setStatus(0);
        ArticleService s = service();
        assertDoesNotThrow(() -> s.create(dto, 1L), "草稿必须允许只有标题");
    }

    @Test
    void create_publishWithoutContent_isRejected() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("空文章");
        dto.setStatus(1);
        dto.setContentMd("   ");
        ArticleService s = service();
        BusinessException ex = assertThrows(BusinessException.class, () -> s.create(dto, 1L));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("正文"));
    }

    // ---------------- slug 冲突 ----------------

    @Test
    void create_duplicateSlug_returns400InsteadOf500() {
        when(articleMapper.countBySlugIncludingDeleted("taken-url", null)).thenReturn(1L);
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("标题");
        dto.setSlug("taken-url");
        dto.setStatus(0);
        ArticleService s = service();
        BusinessException ex = assertThrows(BusinessException.class, () -> s.create(dto, 1L));
        assertEquals(400, ex.getCode(), "slug 冲突应当是 400 而不是兜底的 500");
        assertTrue(ex.getMessage().contains("已被占用"));
    }

    @Test
    void slugAvailable_blankOrTaken_returnsFalse() {
        ArticleService s = service();
        assertFalse(s.slugAvailable("  ", null));
        assertFalse(s.slugAvailable(null, null));
        when(articleMapper.countBySlugIncludingDeleted("free-url", null)).thenReturn(0L);
        assertTrue(s.slugAvailable("free-url", null));
    }

    // ---------------- slug 稳定性 ----------------

    @Test
    void update_publishedArticleChangingTitle_keepsSlug() {
        Article existing = new Article();
        existing.setId(5L);
        existing.setStatus(1);
        existing.setTitle("旧标题");
        existing.setSlug("old-slug");
        existing.setContentMd("正文");
        when(articleMapper.selectById(5L)).thenReturn(existing);

        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("新标题");
        dto.setSlug("");
        dto.setStatus(1);
        dto.setContentMd("正文");

        ArticleService s = service();
        s.update(5L, dto, 9L, false);

        Article updated = capturedUpdate();
        assertEquals("old-slug", updated.getSlug(), "已发布文章的 URL 不能因改标题而变，否则外链全 404");
    }

    @Test
    void update_draftChangingTitle_regeneratesSlug() {
        Article existing = new Article();
        existing.setId(6L);
        existing.setStatus(0);
        existing.setTitle("旧标题");
        existing.setSlug("old-slug");
        existing.setContentMd("正文");
        when(articleMapper.selectById(6L)).thenReturn(existing);

        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("新标题");
        dto.setSlug("");
        dto.setStatus(0);
        dto.setContentMd("正文");

        ArticleService s = service();
        s.update(6L, dto, 9L, false);

        assertNotEquals("old-slug", capturedUpdate().getSlug(), "草稿改标题应跟随生成新 slug");
    }

    private Article capturedUpdate() {
        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ---------------- 版本快照 ----------------

    @Test
    void update_contentUnchanged_doesNotWriteRevision() {
        Article existing = new Article();
        existing.setId(7L);
        existing.setStatus(0);
        existing.setTitle("标题");
        existing.setSlug("s");
        existing.setContentMd("内容没变");
        when(articleMapper.selectById(7L)).thenReturn(existing);

        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("标题");
        dto.setSlug("s");
        dto.setStatus(0);
        dto.setContentMd("内容没变");

        ArticleService s = service();
        s.update(7L, dto, 1L, false);

        verify(articleRevisionMapper, org.mockito.Mockito.never()).insert(any(ArticleRevision.class));
    }

    @Test
    void update_autosave_withinThrottleWindow_doesNotWriteRevision() {
        Article existing = new Article();
        existing.setId(8L);
        existing.setStatus(0);
        existing.setTitle("标题");
        existing.setSlug("s");
        existing.setContentMd("旧内容");
        when(articleMapper.selectById(8L)).thenReturn(existing);
        // 刚刚才存过快照 → 自动保存应当被节流住
        when(articleRevisionMapper.selectLatestTime(8L)).thenReturn(LocalDateTime.now().minusMinutes(1));

        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("标题");
        dto.setSlug("s");
        dto.setStatus(0);
        dto.setContentMd("新内容");

        ArticleService s = service();
        s.update(8L, dto, 1L, true);

        verify(articleRevisionMapper, org.mockito.Mockito.never()).insert(any(ArticleRevision.class));
    }

    @Test
    void update_manualSave_writesRevisionSnapshot() {
        Article existing = new Article();
        existing.setId(9L);
        existing.setStatus(0);
        existing.setTitle("标题");
        existing.setSlug("s");
        existing.setContentMd("旧内容");
        when(articleMapper.selectById(9L)).thenReturn(existing);

        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("标题");
        dto.setSlug("s");
        dto.setStatus(0);
        dto.setContentMd("新内容");

        ArticleService s = service();
        s.update(9L, dto, 1L, false);

        ArgumentCaptor<ArticleRevision> captor = ArgumentCaptor.forClass(ArticleRevision.class);
        verify(articleRevisionMapper).insert(captor.capture());
        ArticleRevision snapshot = captor.getValue();
        assertEquals("旧内容", snapshot.getContentMd(), "快照存的应是「被覆盖掉的那一版」");
        assertEquals(9L, snapshot.getArticleId());
        assertEquals(1L, snapshot.getEditorId());
    }

    // ---------------- 回收站 ----------------

    @Test
    void trashPage_paginatesAndCounts() {
        when(articleMapper.countTrash(null)).thenReturn(12L);
        when(articleMapper.selectTrashPage(null, 0, 10)).thenReturn(List.of(article(1L, 0)));

        ArticleService s = service();
        Map<String, Object> result = s.trashPage(1, 10, null);

        assertEquals(12L, result.get("total"));
        assertEquals(2L, result.get("pages"));
        assertEquals(1, result.get("current"));
    }

    @Test
    void restoreFromTrash_notInTrash_throws404() {
        when(articleMapper.restoreById(3L)).thenReturn(0);
        ArticleService s = service();
        BusinessException ex = assertThrows(BusinessException.class, () -> s.restoreFromTrash(3L));
        assertEquals(404, ex.getCode());
    }

    // ---------------- 定时发布 ----------------

    @Test
    void promoteScheduledArticles_nothingDue_returnsZero() {
        when(articleMapper.selectList(any())).thenReturn(List.of());
        ArticleService s = service();
        assertEquals(0, s.promoteScheduledArticles());
    }

    @Test
    void create_scheduledInFuture_keepsDraftStatus() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("明天早上发");
        dto.setStatus(0);
        dto.setContentMd("正文");
        dto.setPublishAt(LocalDateTime.now().plusDays(1));

        ArticleService s = service();
        s.create(dto, 1L);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getStatus(), "未来的定时时间应保持草稿状态");
        assertNotNull(captor.getValue().getPublishAt());
    }

    @Test
    void create_scheduledInPast_publishesImmediately() {
        LocalDateTime chosen = LocalDateTime.now().minusHours(1);
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("时间已过");
        dto.setStatus(0);
        dto.setContentMd("正文");
        dto.setPublishAt(chosen);

        ArticleService s = service();
        s.create(dto, 1L);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getStatus(), "定时时间已过应视同立即发布");
        // 旧断言是 assertNull(publishAt)：那时 publish_at 只表示「未来的定时时刻」，
        // 发布时会被清掉。现在它同时是「发布时间」（首页热力图与卡片日期都靠它），
        // 所以已发布文章必须保留用户选的时间，不能清成 NULL。
        assertEquals(chosen, captor.getValue().getPublishAt(), "立即发布后应保留用户选的发布时间");
    }

    @Test
    void create_publishedWithoutTime_usesNow() {
        ArticleDTO dto = new ArticleDTO();
        dto.setTitle("直接发布");
        dto.setStatus(1);
        dto.setContentMd("正文");
        // 没传 publishAt：发布时间必填，但前端已默认预填；后端也要兜底成当前时间，
        // 不能留 NULL，否则热力图上这篇会退回 create_time、和卡片显示的日期不一致。
        LocalDateTime before = LocalDateTime.now().minusSeconds(2);
        ArticleService s = service();
        s.create(dto, 1L);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleMapper).insert(captor.capture());
        LocalDateTime at = captor.getValue().getPublishAt();
        assertNotNull(at, "立即发布必须落一个发布时间");
        assertTrue(!at.isBefore(before) && !at.isAfter(LocalDateTime.now().plusSeconds(2)),
                "未指定时间时应取当前时间，实际 " + at);
    }

    // ---------------- 导入 / 导出 ----------------

    @Test
    void exportMarkdown_writesFrontMatterAndTags() {
        Article a = new Article();
        a.setId(1L);
        a.setTitle("导出测试");
        a.setSlug("export-test");
        a.setStatus(1);
        a.setContentMd("# 正文标题");
        a.setCreateTime(LocalDateTime.of(2026, 9, 11, 10, 30));
        when(articleMapper.selectById(1L)).thenReturn(a);

        ArticleTag at = new ArticleTag();
        at.setArticleId(1L);
        at.setTagId(7L);
        when(articleTagMapper.selectList(any())).thenReturn(List.of(at));
        Tag tag = new Tag();
        tag.setId(7L);
        tag.setName("Java");
        when(tagMapper.selectBatchIds(any())).thenReturn(List.of(tag));

        String md = service().exportMarkdown(1L);

        assertTrue(md.startsWith("---\n"), "导出应带 front matter");
        assertTrue(md.contains("title: 导出测试"));
        assertTrue(md.contains("tags: Java"));
        assertTrue(md.contains("status: published"));
        assertTrue(md.endsWith("# 正文标题"), "front matter 之后应当就是正文");
    }

    @Test
    void importMd_parsesFrontMatter() throws Exception {
        String content = "---\n"
                + "title: 带元信息的文章\n"
                + "slug: imported-post\n"
                + "series: 迁移记录\n"
                + "tags: Java, Spring\n"
                + "---\n\n"
                + "正文第一行";
        MockMultipartFile file = new MockMultipartFile(
                "file", "whatever.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));

        Article saved = service().importMd(file, 1L);

        assertEquals("带元信息的文章", saved.getTitle(), "标题应取自 front matter 而不是文件名");
        assertEquals("imported-post", saved.getSlug());
        assertEquals("迁移记录", saved.getSeries());
        assertEquals("正文第一行", saved.getContentMd(), "front matter 不应混进正文");
    }

    @Test
    void importMd_withoutFrontMatter_fallsBackToFilename() throws Exception {
        String content = "# 普通 Markdown\n\n没有任何 front matter";
        MockMultipartFile file = new MockMultipartFile(
                "file", "我的旧文章.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));

        Article saved = service().importMd(file, 1L);

        assertEquals("我的旧文章", saved.getTitle());
        assertTrue(saved.getContentMd().startsWith("# 普通 Markdown"), "无 front matter 时全文都是正文");
    }
}
