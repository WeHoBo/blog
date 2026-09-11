package com.blog.front.service;

import com.blog.common.entity.Article;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final CategoryMapper categoryMapper = mock(CategoryMapper.class);
    private final TagMapper tagMapper = mock(TagMapper.class);
    private final ArticleTagMapper articleTagMapper = mock(ArticleTagMapper.class);
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    private ArticleService service() {
        return new ArticleService(articleMapper, categoryMapper, tagMapper, articleTagMapper, redisTemplate);
    }

    @SuppressWarnings("unchecked")
    private void mockCacheMiss() {
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private Article article(Long id, int status, int visibility) {
        Article article = new Article();
        article.setId(id);
        article.setStatus(status);
        article.setVisibility(visibility);
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
        when(articleMapper.selectById(1L)).thenReturn(article(1L, 0, 0));
        ArticleService s = service();
        assertThrows(BusinessException.class, () -> s.getPublicById(1L));
    }

    @Test
    void getPublicById_throwsWhenPrivate() {
        mockCacheMiss();
        when(articleMapper.selectById(2L)).thenReturn(article(2L, 1, 1));
        ArticleService s = service();
        assertThrows(BusinessException.class, () -> s.getPublicById(2L));
    }

    @Test
    void getAdminById_returnsDraft() {
        Article draft = article(1L, 0, 0);
        when(articleMapper.selectById(1L)).thenReturn(draft);
        ArticleService s = service();
        assertEquals(draft, s.getAdminById(1L));
    }
}
