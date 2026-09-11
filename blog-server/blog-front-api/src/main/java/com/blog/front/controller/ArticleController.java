package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.entity.User;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.front.service.ArticleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    /** 归档 / 系列聚合结果缓存 */
    private static final String CACHE_KEY_ARCHIVE = "article:archive";
    private static final String CACHE_KEY_SERIES = "article:series";
    private static final Duration AGG_CACHE_TTL = Duration.ofMinutes(10);

    private final ArticleService articleService;
    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 读取聚合缓存。Redis 不可用时静默降级为「无缓存直查」，
     * 不让缓存故障影响这两个只读接口的可用性。
     */
    @SuppressWarnings("unchecked")
    private <T> T readAggCache(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeAggCache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, AGG_CACHE_TTL);
        } catch (Exception ignored) {
            // 缓存写入失败不影响接口返回
        }
    }

    /** 文章变更后让归档 / 系列聚合缓存失效 */
    private void evictAggCache() {
        try {
            redisTemplate.delete(List.of(CACHE_KEY_ARCHIVE, CACHE_KEY_SERIES));
        } catch (Exception ignored) {
            // 缓存失效失败时等待 TTL 自然过期
        }
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort) {
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = new ArrayList<>();
            categoryIds.add(categoryId);
            List<Category> children = categoryMapper.selectList(
                    new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
            categoryIds.addAll(children.stream().map(Category::getId).collect(Collectors.toList()));
        }
        Page<Article> page = articleService.page(pageNum, pageSize, categoryId, tagId, keyword, categoryIds, sort);
        List<Article> articles = page.getRecords();
        List<Map<String, Object>> records = new ArrayList<>();
        if (!articles.isEmpty()) {
            // 批量查分类名
            List<Long> categoryIdsOfArticles = articles.stream()
                    .map(Article::getCategoryId)
                    .filter(java.util.Objects::nonNull)
                    .distinct().collect(Collectors.toList());
            Map<Long, String> categoryNameMap = categoryIdsOfArticles.isEmpty() ? Map.of()
                    : categoryMapper.selectBatchIds(categoryIdsOfArticles).stream()
                    .collect(Collectors.toMap(Category::getId, Category::getName));
            // 批量查作者
            List<Long> userIds = articles.stream()
                    .map(Article::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .distinct().collect(Collectors.toList());
            Map<Long, User> userMap = userIds.isEmpty() ? Map.of()
                    : userMapper.selectBatchIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u));
            for (Article a : articles) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", a.getId());
                item.put("title", a.getTitle());
                item.put("summary", a.getSummary());
                item.put("cover", a.getCover());
                item.put("viewCount", a.getViewCount());
                item.put("commentCount", a.getCommentCount());
                item.put("isTop", a.getIsTop());
                item.put("categoryId", a.getCategoryId());
                item.put("categoryName", a.getCategoryId() != null ? categoryNameMap.get(a.getCategoryId()) : null);
                item.put("createTime", a.getCreateTime());
                item.put("wordCount", a.getWordCount() != null ? a.getWordCount() : 0);
                if (a.getUserId() != null) {
                    User author = userMap.get(a.getUserId());
                    if (author != null) {
                        Map<String, Object> authorMap = new HashMap<>();
                        authorMap.put("nickname", author.getNickname() != null ? author.getNickname() : author.getUsername());
                        authorMap.put("avatar", author.getAvatar());
                        item.put("author", authorMap);
                    }
                }
                records.add(item);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("current", page.getCurrent());
        return Result.ok(result);
    }

    @GetMapping("/post/{slug}")
    public Result<Map<String, Object>> bySlug(@PathVariable String slug) {
        Article article = articleMapper.selectOne(
                new LambdaQueryWrapper<Article>().eq(Article::getSlug, slug));
        if (article == null) return Result.fail(404, "文章不存在");
        return detail(article.getId());
    }

    @GetMapping("/{id:\\d+}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Article article = isAdmin() ? articleService.getAdminById(id) : articleService.getPublicById(id);
        List<Tag> tags = articleService.getTagsByArticleId(id);
        Map<String, Object> result = new HashMap<>();
        result.put("article", article);
        result.put("tags", tags);
        if (article.getUserId() != null) {
            User author = userMapper.selectById(article.getUserId());
            if (author != null) {
                Map<String, Object> authorMap = new HashMap<>();
                authorMap.put("id", author.getId());
                authorMap.put("username", author.getUsername());
                authorMap.put("nickname", author.getNickname() != null ? author.getNickname() : author.getUsername());
                authorMap.put("avatar", author.getAvatar());
                result.put("author", authorMap);
            }
        }
        return Result.ok(result);
    }

    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(articleService.listCategories());
    }

    @GetMapping("/tags")
    public Result<List<Tag>> tags() {
        return Result.ok(articleService.listTags());
    }

    @GetMapping("/{id}/related")
    public Result<List<Article>> related(@PathVariable Long id) {
        return Result.ok(articleService.related(id));
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/admin/list")
    public Result<Page<Article>> adminList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long categoryId) {
        return Result.ok(articleService.adminPage(pageNum, pageSize, keyword, status, categoryId));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<Article> create(@Valid @RequestBody ArticleDTO dto) {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Article created = articleService.create(dto, userId);
        evictAggCache();
        return Result.ok(created);
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Article> update(@PathVariable Long id, @Valid @RequestBody ArticleDTO dto) {
        Article updated = articleService.update(id, dto);
        evictAggCache();
        return Result.ok(updated);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        articleService.delete(id);
        evictAggCache();
        return Result.ok();
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/import")
    public Result<Article> importMd(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Result.ok(articleService.importMd(file, userId));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/import-word")
    public Result<Article> importWord(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Result.ok(articleService.importWord(file, userId));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/batch-delete")
    public Result<?> batchDelete(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            articleService.delete(id);
        }
        evictAggCache();
        return Result.ok();
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/batch-export")
    public void batchExport(@RequestBody List<Long> ids, HttpServletResponse response) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            Article article = articleService.getAdminById(id);
            sb.append("---\n");
            sb.append("title: ").append(article.getTitle()).append("\n");
            sb.append("date: ").append(article.getCreateTime()).append("\n");
            sb.append("---\n\n");
            sb.append(article.getContentMd() != null ? article.getContentMd() : "").append("\n\n");
        }
        String filename = "articles-export.md";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/export")
    public void exportMd(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Article article = articleService.getPublicById(id);
        String content = article.getContentMd() != null ? article.getContentMd() : "";
        String filename = article.getTitle() + ".md";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/archive")
    public Result<Map<String, Object>> archive() {
        Map<String, Object> cached = readAggCache(CACHE_KEY_ARCHIVE);
        if (cached != null) {
            return Result.ok(cached);
        }
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .eq(Article::getIsDeleted, 0)
                        .select(Article::getId, Article::getTitle, Article::getCreateTime)
                        .orderByDesc(Article::getCreateTime));

        Map<Integer, Map<Integer, List<Map<String, Object>>>> yearMap = new LinkedHashMap<>();
        for (Article a : articles) {
            if (a.getCreateTime() == null) continue;
            int year = a.getCreateTime().getYear();
            int month = a.getCreateTime().getMonthValue();
            yearMap.putIfAbsent(year, new LinkedHashMap<>());
            yearMap.get(year).putIfAbsent(month, new ArrayList<>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("createTime", a.getCreateTime().toString().substring(0, 10));
            yearMap.get(year).get(month).add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> yearList = new ArrayList<>();
        for (Map.Entry<Integer, Map<Integer, List<Map<String, Object>>>> ye : yearMap.entrySet()) {
            Map<String, Object> yearItem = new LinkedHashMap<>();
            yearItem.put("year", ye.getKey());
            List<Map<String, Object>> monthList = new ArrayList<>();
            for (Map.Entry<Integer, List<Map<String, Object>>> me : ye.getValue().entrySet()) {
                Map<String, Object> monthItem = new LinkedHashMap<>();
                monthItem.put("month", me.getKey());
                monthItem.put("count", me.getValue().size());
                monthItem.put("articles", me.getValue());
                monthList.add(monthItem);
            }
            monthList.sort((a, b) -> (int) b.get("month") - (int) a.get("month"));
            yearItem.put("months", monthList);
            yearList.add(yearItem);
        }
        result.put("archives", yearList);
        writeAggCache(CACHE_KEY_ARCHIVE, result);
        return Result.ok(result);
    }

    @GetMapping("/series")
    public Result<List<Map<String, Object>>> seriesList() {
        List<Map<String, Object>> cached = readAggCache(CACHE_KEY_SERIES);
        if (cached != null) {
            return Result.ok(cached);
        }
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .eq(Article::getIsDeleted, 0)
                        .isNotNull(Article::getSeries)
                        .ne(Article::getSeries, "")
                        .select(Article::getSeries)
                        .orderByAsc(Article::getSeries));
        Map<String, Long> seriesCount = new LinkedHashMap<>();
        for (Article a : articles) {
            seriesCount.merge(a.getSeries(), 1L, Long::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Long> e : seriesCount.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", e.getKey());
            item.put("count", e.getValue());
            result.add(item);
        }
        writeAggCache(CACHE_KEY_SERIES, result);
        return Result.ok(result);
    }
}
