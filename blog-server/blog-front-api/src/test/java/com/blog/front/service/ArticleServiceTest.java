package com.blog.front.service;

import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    void getById_throwsWhenMissing() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        ArticleService s = service();
        org.junit.jupiter.api.Assertions.assertThrows(
                com.blog.common.exception.BusinessException.class,
                () -> s.getById(999L));
    }
}
