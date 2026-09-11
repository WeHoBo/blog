package com.blog.front.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.entity.Article;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    /**
     * LambdaQueryWrapper.select(方法引用...) 会立即解析列名，依赖 MyBatis-Plus 的
     * TableInfo 缓存。这里全部 mapper 都是 mock，没有 Spring/MyBatis 启动过程，
     * 缓存为空会抛 "can not find lambda cache for this entity"，故手动初始化。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
    }

    private ArticleService service() {
        return new ArticleService(articleMapper, categoryMapper, tagMapper, articleTagMapper, redisTemplate);
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
    void adminPage_selectsOnlyListColumns_excludesBodyAndPassword() {
        ArticleService s = service();
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        when(articleMapper.selectPage(any(), captor.capture())).thenReturn(new Page<>());

        s.adminPage(1, 10, null, null, null);

        String select = captor.getValue().getSqlSelect();
        assertNotNull(select, "后台列表查询应显式指定返回列");
        assertFalse(select.contains("content_md"));
        assertFalse(select.contains("content_html"));
        assertFalse(select.contains("password"), "后台列表不应下发密码字段");
    }
}
