package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.entity.Article;
import com.blog.common.entity.ArticleTag;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import com.blog.front.util.DocxToMdUtil;
import com.blog.front.util.WordCounter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public Page<Article> page(int pageNum, int pageSize, Long categoryId, Long tagId, String keyword, List<Long> categoryIds, String sort) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0);
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(Article::getCategoryId, categoryIds);
        } else if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }
        if (tagId != null) {
            List<ArticleTag> ats = articleTagMapper.selectList(
                    new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
            List<Long> ids = ats.stream().map(ArticleTag::getArticleId).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                wrapper.in(Article::getId, ids);
            } else {
                wrapper.eq(Article::getId, -1L);
            }
        }
        if ("title".equals(sort)) {
            wrapper.last("ORDER BY is_top DESC, CAST(title AS UNSIGNED) ASC, title ASC");
        } else {
            wrapper.orderByDesc(Article::getIsTop)
                    .orderByDesc(Article::getCreateTime);
        }
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Page<Article> adminPage(int pageNum, int pageSize, String keyword, Integer status, Long categoryId) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDeleted, 0);
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }
        wrapper.orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreateTime);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Article getById(Long id) {
        String key = "article:" + id;
        Article article = (Article) redisTemplate.opsForValue().get(key);
        if (article != null) {
            return article;
        }
        article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        article.setViewCount(article.getViewCount() + 1);
        articleMapper.updateById(article);
        redisTemplate.opsForValue().set(key, article, Duration.ofMinutes(10));
        return article;
    }

    @Transactional
    public Article create(ArticleDTO dto, Long userId) {
        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setSlug(dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : generateSlug(dto.getTitle()));
        article.setContentMd(dto.getContentMd());
        article.setContentHtml(dto.getContentHtml());
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus() != null ? dto.getStatus() : 0);
        article.setIsTop(dto.getIsTop() != null ? dto.getIsTop() : 0);
        article.setWordCount(WordCounter.count(dto.getContentMd()));
        article.setUserId(userId);
        articleMapper.insert(article);

        if (dto.getTagIds() != null && !dto.getTagIds().isEmpty()) {
            for (Long tagId : dto.getTagIds()) {
                ArticleTag at = new ArticleTag();
                at.setArticleId(article.getId());
                at.setTagId(tagId);
                articleTagMapper.insert(at);
            }
        }

        if (dto.getCategoryId() != null) {
            Category category = categoryMapper.selectById(dto.getCategoryId());
            if (category != null) {
                category.setArticleCount(category.getArticleCount() + 1);
                categoryMapper.updateById(category);
            }
        }
        return article;
    }

    @Transactional
    public Article update(Long id, ArticleDTO dto) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        article.setTitle(dto.getTitle());
        if (dto.getSlug() != null && !dto.getSlug().isBlank()) {
            article.setSlug(dto.getSlug());
        }
        if (!article.getTitle().equals(dto.getTitle()) && (dto.getSlug() == null || dto.getSlug().isBlank())) {
            article.setSlug(generateSlug(dto.getTitle()));
        }
        article.setContentMd(dto.getContentMd());
        article.setContentHtml(dto.getContentHtml());
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus());
        article.setIsTop(dto.getIsTop());
        article.setWordCount(WordCounter.count(dto.getContentMd()));
        articleMapper.updateById(article);
        redisTemplate.delete("article:" + id);
        return article;
    }

    @Transactional
    public void delete(Long id) {
        int rows = articleMapper.deleteById(id);
        if (rows == 0) {
            throw new BusinessException("文章不存在");
        }
        redisTemplate.delete("article:" + id);
    }

    public List<Category> listCategories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
    }

    public List<Tag> listTags() {
        return tagMapper.selectList(null);
    }

    public List<Article> related(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getCategoryId() == null) {
            return List.of();
        }
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getCategoryId, article.getCategoryId())
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0)
                .ne(Article::getId, id)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT 4"));
    }

    public List<Tag> getTagsByArticleId(Long articleId) {
        List<ArticleTag> ats = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (ats.isEmpty()) return List.of();
        List<Long> tagIds = ats.stream().map(ArticleTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds);
    }

    public List<Article> getArticlesByTagId(Long tagId) {
        List<ArticleTag> ats = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
        if (ats.isEmpty()) return List.of();
        List<Long> articleIds = ats.stream().map(ArticleTag::getArticleId).toList();
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0)
                .in(Article::getId, articleIds)
                .orderByDesc(Article::getCreateTime));
    }

    @Transactional
    public Article importMd(MultipartFile file, Long userId) throws IOException {
        String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String filename = file.getOriginalFilename();
        String title = filename != null ? filename.replace(".md", "").replace(".MD", "") : "未命名";

        Article article = new Article();
        article.setTitle(title);
        article.setContentMd(content);
        article.setUserId(userId);
        article.setStatus(0);
        article.setIsTop(0);
        article.setWordCount(WordCounter.count(content));
        article.setSlug(generateSlug(title));
        articleMapper.insert(article);
        return article;
    }

    @Transactional
    public Article importWord(MultipartFile file, Long userId) throws IOException {
        String filename = file.getOriginalFilename();
        String title = filename != null ? filename.replace(".docx", "").replace(".DOCX", "").replace(".doc", "").replace(".DOC", "") : "未命名";
        try {
            String content = DocxToMdUtil.convert(file.getInputStream());
            Article article = new Article();
            article.setTitle(title);
            article.setContentMd(content);
            article.setUserId(userId);
            article.setStatus(0);
            article.setIsTop(0);
            article.setWordCount(WordCounter.count(content));
            article.setSlug(generateSlug(title));
            articleMapper.insert(article);
            return article;
        } catch (Exception e) {
            throw new BusinessException("Word 解析失败: " + e.getMessage());
        }
    }

    private String generateSlug(String title) {
        String slug = StrUtil.toUnderlineCase(title).replace('_', '-');
        return slug + "-" + System.currentTimeMillis();
    }
}
