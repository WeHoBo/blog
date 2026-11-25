package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.constant.CategoryConstants;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.ArticleRevision;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.entity.User;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.common.exception.BusinessException;
import com.blog.front.cache.ArticleCacheKeys;
import com.blog.front.service.ArticleService;
import com.blog.front.service.RagIndexClient;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 文章下线时同步清掉知识库里的向量（失败只记日志，全量对账兜底） */
    private final RagIndexClient ragIndexClient;

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
            redisTemplate.opsForValue().set(key, value, ArticleCacheKeys.AGG_TTL);
        } catch (Exception ignored) {
            // 缓存写入失败不影响接口返回
        }
    }

    /** 文章变更后让归档 / 系列聚合缓存失效 */
    private void evictAggCache() {
        try {
            redisTemplate.delete(List.of(ArticleCacheKeys.ARCHIVE, ArticleCacheKeys.SERIES));
        } catch (Exception ignored) {
            // 缓存失效失败时等待 TTL 自然过期
        }
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_admin".equals(a.getAuthority()));
    }

    /** 当前登录用户 id（用于写入版本快照的「修改人」） */
    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sort,
            // 精品过滤：1 只要 / 0 排除 / 不传 不限。首页布局改由置顶驱动，这个维度保留给后台筛选
            @RequestParam(required = false) Integer featured,
            // 置顶过滤：1 只要置顶 / 0 排除置顶。首页中间取 1、右侧「全部文章」取 0
            @RequestParam(required = false) Integer top) {
        Page<Article> page = articleService.page(pageNum, pageSize, categoryId, tagId, keyword, null, sort, featured, top);
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
                item.put("slug", a.getSlug());
                item.put("summary", a.getSummary());
                item.put("cover", a.getCover());
                item.put("viewCount", a.getViewCount());
                item.put("commentCount", a.getCommentCount());
                item.put("isTop", a.getIsTop());
                // 只影响卡片上的「精品」徽标，不决定首页布局（首页由 isTop 驱动）
                item.put("isFeatured", a.getIsFeatured());
                item.put("categoryId", a.getCategoryId());
                // 历史数据可能没写 category_id（新数据在保存时已落到默认分类）：展示层统一兜底，
                // 保证列表里永远显示得出分类名，不会出现空白
                item.put("categoryName", a.getCategoryId() != null
                        ? categoryNameMap.getOrDefault(a.getCategoryId(), CategoryConstants.DEFAULT_CATEGORY_NAME)
                        : CategoryConstants.DEFAULT_CATEGORY_NAME);
                // 发布时间：卡片日期口径是 `publishAt || createTime`（前端已这么写），
                // 但以前列表接口没返回这个字段，于是永远回落到创建时间 —— 定时发布的文章日期会偏早。
                item.put("publishAt", a.getPublishAt());
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

    /**
     * 服务端渲染标记。前端 SSR 期间请求详情接口时会带这个头，表示「这只是一次渲染，别记阅读数」——
     * 页面 HTML 上了 routeRules 缓存后，同一 URL 在缓存窗口内只渲染一次，按 GET 计数会漏记。
     * 浏览器挂载后会调 {@code POST /api/article/{id}/view} 补记，一次访问记一次。
     */
    private static final String CLIENT_SSR = "ssr";

    @GetMapping("/post/{slug}")
    public Result<Map<String, Object>> bySlug(@PathVariable String slug,
                                              @RequestHeader(value = "X-Blog-Client", required = false) String client) {
        Article article = articleMapper.selectOne(
                new LambdaQueryWrapper<Article>().select(Article::getId).eq(Article::getSlug, slug));
        if (article == null) return Result.fail(404, "文章不存在");
        return detail(article.getId(), client);
    }

    @GetMapping("/{id:\\d+}")
    public Result<Map<String, Object>> detail(@PathVariable Long id,
                                              @RequestHeader(value = "X-Blog-Client", required = false) String client) {
        boolean countView = !CLIENT_SSR.equalsIgnoreCase(client == null ? "" : client.trim());
        Article article = isAdmin()
                ? articleService.getAdminById(id)
                : articleService.getPublicById(id, countView);
        List<Tag> tags = articleService.getTagsByArticleId(id);
        Map<String, Object> result = new HashMap<>();
        if (isAdmin()) articleService.attachEditVersion(article, tags);
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

    /**
     * 阅读数打点。浏览器挂载后调一次：缓存命中的页面没有 SSR 请求，靠它把这次访问记上。
     * 返回最新阅读数，前端可就地刷新数字。
     */
    @PostMapping("/{id:\\d+}/view")
    public Result<Integer> view(@PathVariable Long id) {
        return Result.ok(articleService.incrementViewCount(id));
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

    /** 上一篇 / 下一篇（只返回 id/title/slug，避免为了算相邻文章而拉取整页列表） */
    @GetMapping("/{id}/neighbors")
    public Result<Map<String, Object>> neighbors(@PathVariable Long id) {
        return Result.ok(articleService.neighbors(id));
    }

    /**
     * 发布热力图：按月返回「每天发了多少篇」（首页左侧日历用）。
     *
     * <p><b>公开接口</b>：首页日历是给所有访客看的，不能挂 admin 注解。
     *
     * <p>⚠️ 历史事故（2026-11-14 发现）：这个方法的 javadoc 和
     * {@code @PreAuthorize("hasRole('admin')")} 曾经和下面 slugAvailable 的注释**插错位置**，
     * 导致管理员注解落到了热力图上、而 slug 探测变成匿名可访问。症状很隐蔽：
     * **管理员登录后一切正常，只有访客看到首页日历整块空白**（接口 403），
     * 在后台反复点都复现不出来。
     *
     * <p>可以安全公开的依据见 {@code ArticleMapper#countPublishedByDay}：
     * 只统计 {@code status=1 AND is_deleted=0}，草稿和回收站的数量不会泄露。
     *
     * @param month 形如 2026-11；不传则取当前月
     */
    @GetMapping("/contribution")
    public Result<Map<String, Object>> contribution(@RequestParam(required = false) String month) {
        YearMonth ym;
        try {
            ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month.trim());
        } catch (Exception e) {
            throw new BusinessException(400, "月份格式应为 YYYY-MM");
        }
        List<Map<String, Object>> rows = articleMapper.countPublishedByDay(
                ym.atDay(1).atStartOfDay(),
                ym.plusMonths(1).atDay(1).atStartOfDay());
        Map<String, Integer> days = new LinkedHashMap<>();
        int total = 0;
        for (Map<String, Object> row : rows) {
            Object d = row.get("day");
            Object c = row.get("cnt");
            if (d == null || !(c instanceof Number n)) {
                continue;
            }
            days.put(String.valueOf(d), n.intValue());
            total += n.intValue();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("month", ym.toString());
        data.put("days", days);
        data.put("total", total);
        return Result.ok(data);
    }

    /**
     * 自定义 URL 可用性预检。编辑器在 slug 失焦时调用，
     * 把「唯一键冲突」这类只有保存时才会暴露的问题提前到输入阶段。
     *
     * <p>需要管理员：它能探测「某个还没发布的 slug 是否已被占用」，属于后台能力。
     * 前端只有 {@code ArticleEditor.vue} 调用它，公开页面不用。
     */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/slug-available")
    public Result<Map<String, Object>> slugAvailable(
            @RequestParam String slug,
            @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new HashMap<>();
        result.put("available", articleService.slugAvailable(slug, excludeId));
        return Result.ok(result);
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
        Article created = articleService.create(dto, currentUserId());
        evictAggCache();
        return Result.ok(created);
    }

    /**
     * @param autosave 编辑器的自动保存会带上它：不影响保存结果，只影响「是否留版本快照」
     *                 （自动保存每 10 分钟最多留一版，避免把版本历史刷满）
     */
    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Article> update(@PathVariable Long id,
                                  @Valid @RequestBody ArticleDTO dto,
                                  @RequestParam(defaultValue = "false") boolean autosave) {
        Article updated = articleService.update(id, dto, currentUserId(), autosave);
        evictAggCache();
        // 下线时清知识库向量这一步在 ArticleService.update 里做（它才拿得到原状态，
        // 而且只在「公开 → 非公开」时触发，不会让草稿的自动保存每 5 秒打一次 RAG）
        return Result.ok(updated);
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        articleService.delete(id);
        evictAggCache();
        ragIndexClient.evictArticle(id);
        return Result.ok();
    }

    /**
     * 设为 / 取消「精品」（首页横幅中间展示的优质文章）。
     *
     * <p>只给超管：普通管理员发布的文章统一进右侧「全部文章」，不能自己挑自己当精品。
     * 这里用独立接口而不是走整篇 {@code PUT /{id}}，是为了让后台列表能一键切换，
     * 不必把整篇正文读出来再写回去。
     */
    @PreAuthorize("hasRole('super')")
    @PutMapping("/{id:\\d+}/featured")
    public Result<Article> setFeatured(@PathVariable Long id,
                                       @RequestBody Map<String, Object> body) {
        Object raw = body.get("featured");
        int featured = (raw instanceof Boolean b) ? (b ? 1 : 0)
                : (raw != null && "1".equals(String.valueOf(raw))) ? 1 : 0;
        Article updated = articleService.setFeatured(id, featured);
        // 首页有 20s 缓存，不清的话要等一轮才看得见
        evictAggCache();
        return Result.ok(updated);
    }

    // =================================================================
    // 作者投稿的审核（只有超管能操作）
    // =================================================================

    /**
     * 通过审核：置为已发布，发布时间 = 此刻。
     *
     * <p>用独立接口而不是 {@code PUT /{id}} 改 status，是因为整篇保存要求把正文一起传回来，
     * 审核时前端只有列表里的元信息。而且审核是个不可逆的动作，独立接口更好记审计。
     */
    @PreAuthorize("hasRole('super')")
    @PostMapping("/{id:\\d+}/approve")
    public Result<Article> approve(@PathVariable Long id) {
        Article article = articleService.approve(id);
        evictAggCache();
        return Result.ok(article);
    }

    /** 驳回：置为 3 并留下理由。理由必填 —— 不写的话作者只会反复重交同一篇。 */
    @PreAuthorize("hasRole('super')")
    @PostMapping("/{id:\\d+}/reject")
    public Result<Article> reject(@PathVariable Long id,
                                  @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "" : String.valueOf(body.getOrDefault("reason", ""));
        return Result.ok(articleService.reject(id, reason));
    }

    // =================================================================
    // 版本历史
    // =================================================================

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/{id:\\d+}/revisions")
    public Result<List<ArticleRevision>> revisions(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(articleService.revisions(id, limit));
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/{id:\\d+}/revisions/{revisionId}")
    public Result<ArticleRevision> revisionDetail(@PathVariable Long id,
                                                  @PathVariable Long revisionId) {
        return Result.ok(articleService.revisionDetail(id, revisionId));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id:\\d+}/revisions/{revisionId}/restore")
    public Result<Article> restoreRevision(@PathVariable Long id, @PathVariable Long revisionId) {
        Article restored = articleService.restoreRevision(id, revisionId, currentUserId());
        evictAggCache();
        return Result.ok(restored);
    }

    // =================================================================
    // 回收站
    // =================================================================

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/trash/list")
    public Result<Map<String, Object>> trashList(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(articleService.trashPage(pageNum, pageSize, keyword));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/{id:\\d+}/restore")
    public Result<?> restore(@PathVariable Long id) {
        articleService.restoreFromTrash(id);
        evictAggCache();
        return Result.ok();
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/batch-restore")
    public Result<?> batchRestore(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            articleService.restoreFromTrash(id);
        }
        evictAggCache();
        return Result.ok();
    }

    @PreAuthorize("hasRole('super')")
    @DeleteMapping("/{id:\\d+}/force")
    public Result<?> forceDelete(@PathVariable Long id) {
        articleService.forceDelete(id);
        evictAggCache();
        ragIndexClient.evictArticle(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('super')")
    @PostMapping("/batch-force-delete")
    public Result<?> batchForceDelete(@RequestBody List<Long> ids) {
        for (Long id : ids) {
            articleService.forceDelete(id);
        }
        evictAggCache();
        ragIndexClient.evictArticles(ids);
        return Result.ok();
    }

    // =================================================================
    // 导入 / 导出
    // =================================================================

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/import")
    public Result<Article> importMd(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(articleService.importMd(file, currentUserId()));
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/import-word")
    public Result<Article> importWord(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(articleService.importWord(file, currentUserId()));
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
        writeMarkdownResponse(response, articleService.exportMarkdownBatch(ids), "articles-export.md");
    }

    @PreAuthorize("hasRole('admin')")
    @GetMapping("/{id}/export")
    public void exportMd(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Article article = articleService.getAdminById(id);
        writeMarkdownResponse(response, articleService.exportMarkdown(id),
                normalizeFileName(article.getTitle()) + ".md");
    }

    private void writeMarkdownResponse(HttpServletResponse response, String content, String filename)
            throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }

    private String normalizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "article";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // =================================================================
    // 聚合（归档 / 系列）
    // =================================================================

    @GetMapping("/archive")
    public Result<Map<String, Object>> archive() {
        Map<String, Object> cached = readAggCache(ArticleCacheKeys.ARCHIVE);
        if (cached != null) {
            return Result.ok(cached);
        }
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .eq(Article::getIsDeleted, 0)
                        .select(Article::getId, Article::getTitle, Article::getSlug, Article::getCreateTime, Article::getPublishAt)
                        .last("ORDER BY " + ArticleMapper.PUBLICATION_TIME + " DESC, id DESC"));

        Map<Integer, Map<Integer, List<Map<String, Object>>>> yearMap = new LinkedHashMap<>();
        for (Article a : articles) {
            LocalDateTime date = a.getPublishAt() != null ? a.getPublishAt() : a.getCreateTime();
            if (date == null) continue;
            int year = date.getYear();
            int month = date.getMonthValue();
            yearMap.putIfAbsent(year, new LinkedHashMap<>());
            yearMap.get(year).putIfAbsent(month, new ArrayList<>());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("title", a.getTitle());
            item.put("slug", a.getSlug());
            item.put("createTime", a.getCreateTime() == null ? null : a.getCreateTime().toLocalDate().toString());
            item.put("publishAt", date.toLocalDate().toString());
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
        writeAggCache(ArticleCacheKeys.ARCHIVE, result);
        return Result.ok(result);
    }

    @GetMapping("/series")
    public Result<List<Map<String, Object>>> seriesList() {
        List<Map<String, Object>> cached = readAggCache(ArticleCacheKeys.SERIES);
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
        writeAggCache(ArticleCacheKeys.SERIES, result);
        return Result.ok(result);
    }

    /**
     * 某个系列的全部成员（按阅读顺序），供详情页「第 N 篇 / 共 M 篇」与首页系列目录使用。
     *
     * <p>排序口径按**发布时间升序**：站内没有 series_order 字段，
     * 连载文章的发布时间通常就是写作顺序，先用它凑合。
     * 将来要给作者手动排序，只改这里的比较器（加一列），前端不用动。
     *
     * <p>⚠️ 排序放在 Java 侧做，不用 {@code orderByAsc(Article::getPublishAt)}：
     * MySQL 的 ASC 会把 NULL 排在最前面，历史文章的 publish_at 多为 NULL，
     * 一升序它们就跑到系列开头去了。这里用 publish_at，为空回落到 create_time。
     *
     * <p>不做 Redis 缓存：一次带索引的 selectList 而已，
     * 加了反而要处理「系列新增成员」的失效（现有 evictAggCache 只删固定的两个 key）。
     */
    @GetMapping("/series/detail")
    public Result<Map<String, Object>> seriesDetail(@RequestParam("name") String name) {
        String series = name == null ? "" : name.trim();
        if (series.isEmpty()) {
            throw new BusinessException(400, "系列名不能为空");
        }
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .eq(Article::getIsDeleted, 0)
                        .eq(Article::getSeries, series)
                        .select(Article::getId, Article::getTitle, Article::getSlug,
                                Article::getCover, Article::getSummary, Article::getWordCount,
                                Article::getPublishAt, Article::getCreateTime));
        Comparator<Article> byTimeline = Comparator.comparing(
                (Article a) -> a.getPublishAt() != null ? a.getPublishAt() : a.getCreateTime(),
                Comparator.nullsLast(Comparator.naturalOrder()));
        articles.sort(byTimeline.thenComparing(Article::getId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", series);
        result.put("count", articles.size());
        result.put("articles", articles);
        return Result.ok(result);
    }
}
