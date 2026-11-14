package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 工具网关：Agent 循环（Python 侧）通过 {@code POST /internal/ai/tool/{name}} 调到这里。
 *
 * <p>为什么工具放在 Java 而不是让 Python 直连 MySQL：
 * 文章的可见性（草稿/回收站）、阅读数打点、缓存失效全都在 {@link ArticleService} 里，
 * Python 绕过去就等于绕过了这些规则 —— 模型能读到草稿，或者每查一次阅读数就 +1。
 *
 * <p><b>约定：任何异常都不抛给调用方</b>，一律转成 {@code {"ok":false,"error":"..."}}。
 * 让模型看到错误文本、自己换一条路，比中断整轮对话对用户友好得多。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiToolService {

    /** 单次检索返回条数上限，也用于防止模型传个 limit=1000 把上下文撑爆 */
    private static final int MAX_LIMIT = 8;

    /** 单篇文章正文最多回给模型的字符数。整篇塞进去会把后续所有轮次的 prompt 一起抬高 */
    private static final int MAX_CONTENT_CHARS = 6000;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ArticleService articleService;

    /** 工具执行的统一结果：digest 给模型看，data 给程序用，sources 给前端渲染来源卡片 */
    public record ToolResult(String digest, Map<String, Object> data, List<Map<String, Object>> sources) {
    }

    /**
     * 执行一个工具。name 由 Python 侧的工具名决定，必须是白名单里的值。
     */
    public Map<String, Object> execute(String name, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            ToolResult result = dispatch(name, args == null ? Map.of() : args);
            out.put("ok", true);
            out.put("digest", result.digest());
            out.put("data", result.data());
            out.put("sources", result.sources());
        } catch (BusinessException e) {
            // 业务上的"没找到"不是故障，当成可重试信息回给模型
            out.put("ok", false);
            out.put("error", e.getMessage());
        } catch (Exception e) {
            log.warn("AI 工具执行失败 name={} args={} err={}", name, args, e.toString());
            out.put("ok", false);
            out.put("error", "工具内部错误：" + e.getClass().getSimpleName());
        }
        log.info("AI 工具 {} 耗时 {}ms ok={}", name, System.currentTimeMillis() - start, out.get("ok"));
        return out;
    }

    private ToolResult dispatch(String name, Map<String, Object> args) {
        if (StrUtil.isBlank(name)) {
            throw new BusinessException(400, "缺少工具名");
        }
        return switch (name) {
            case "search_articles" -> searchArticles(args);
            case "get_article" -> getArticle(args);
            case "related_articles" -> relatedArticles(args);
            case "list_taxonomy" -> listTaxonomy(args);
            case "site_stats" -> siteStats();
            default -> throw new BusinessException(400, "未知工具：" + name);
        };
    }

    // ------------------------------------------------------------------
    // search_articles
    // ------------------------------------------------------------------

    private ToolResult searchArticles(Map<String, Object> args) {
        String q = str(args, "q");
        int limit = clamp(intArg(args, "limit", 5), 1, MAX_LIMIT);
        if (StrUtil.isBlank(q)) {
            throw new BusinessException(400, "q 不能为空");
        }

        Long categoryId = null;
        String categoryName = str(args, "category");
        if (StrUtil.isNotBlank(categoryName)) {
            categoryId = findCategoryId(categoryName);
            if (categoryId == null) {
                return new ToolResult("站内没有名为「" + categoryName + "」的分类，可先用 list_taxonomy 查看有哪些",
                        items(Map.of("items", List.of())), List.of());
            }
        }
        Long tagId = null;
        String tagName = str(args, "tag");
        if (StrUtil.isNotBlank(tagName)) {
            tagId = findTagId(tagName);
            if (tagId == null) {
                return new ToolResult("站内没有名为「" + tagName + "」的标签，可先用 list_taxonomy 查看有哪些",
                        items(Map.of("items", List.of())), List.of());
            }
        }

        Page<Article> page = articleService.page(1, limit, categoryId, tagId, q, null, null);
        List<Article> records = page.getRecords();
        if (records.isEmpty()) {
            return new ToolResult("没有检索到与「" + q + "」相关的站内文章",
                    items(Map.of("items", List.of(), "total", page.getTotal())), List.of());
        }

        List<Long> ids = records.stream().map(Article::getId).toList();
        Map<Long, List<String>> tagMap = articleService.tagNamesByArticleIds(ids);
        Map<Long, String> categoryMap = categoryNameMap();

        List<Map<String, Object>> rows = new ArrayList<>(records.size());
        List<Map<String, Object>> sources = new ArrayList<>(records.size());
        List<String> titles = new ArrayList<>(records.size());
        for (Article a : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("title", a.getTitle());
            row.put("summary", StrUtil.maxLength(StrUtil.blankToDefault(a.getSummary(), ""), 120));
            row.put("tags", tagMap.getOrDefault(a.getId(), List.of()));
            row.put("category", categoryMap.getOrDefault(a.getCategoryId(), ""));
            row.put("url", articleUrl(a));
            row.put("publishedAt", a.getCreateTime() == null ? "" : a.getCreateTime().format(DAY));
            rows.add(row);

            sources.add(source("article", a.getTitle(), articleUrl(a)));
            titles.add("《" + a.getTitle() + "》");
        }

        String digest = "命中 " + page.getTotal() + " 篇，返回 " + records.size() + " 篇：" + String.join("、", titles);
        Map<String, Object> data = items(Map.of("items", rows, "total", page.getTotal()));
        return new ToolResult(digest, data, sources);
    }

    // ------------------------------------------------------------------
    // get_article
    // ------------------------------------------------------------------

    private ToolResult getArticle(Map<String, Object> args) {
        Article article = null;
        Long id = longArg(args, "id");
        if (id != null) {
            article = articleService.getPublicById(id, false);
        } else {
            String slug = str(args, "slug");
            if (StrUtil.isBlank(slug)) {
                throw new BusinessException(400, "id 与 slug 至少要有一个");
            }
            article = articleService.getPublicBySlug(slug);
        }

        String content = StrUtil.blankToDefault(article.getContentMd(), "");
        boolean truncated = content.length() > MAX_CONTENT_CHARS;
        if (truncated) {
            content = content.substring(0, MAX_CONTENT_CHARS) + "\n\n（正文过长，已截断）";
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", article.getId());
        row.put("title", article.getTitle());
        row.put("url", articleUrl(article));
        row.put("category", categoryNameMap().getOrDefault(article.getCategoryId(), ""));
        row.put("tags", articleService.getTagsByArticleId(article.getId()).stream().map(Tag::getName).toList());
        row.put("publishedAt", article.getCreateTime() == null ? "" : article.getCreateTime().format(DAY));
        row.put("content", content);
        row.put("truncated", truncated);

        String digest = "已读取《" + article.getTitle() + "》，正文 "
                + article.getWordCount() + " 字" + (truncated ? "（已截断到前 " + MAX_CONTENT_CHARS + " 字）" : "");
        return new ToolResult(digest, row, List.of(source("article", article.getTitle(), articleUrl(article))));
    }

    // ------------------------------------------------------------------
    // related_articles
    // ------------------------------------------------------------------

    private ToolResult relatedArticles(Map<String, Object> args) {
        Long id = longArg(args, "id");
        if (id == null) {
            throw new BusinessException(400, "缺少 id");
        }
        int limit = clamp(intArg(args, "limit", 4), 1, MAX_LIMIT);
        List<Article> related = articleService.related(id);
        if (related.isEmpty()) {
            return new ToolResult("这篇文章暂无相关推荐", items(Map.of("items", List.of())), List.of());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (Article a : related) {
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("title", a.getTitle());
            row.put("url", articleUrl(a));
            rows.add(row);
            sources.add(source("article", a.getTitle(), articleUrl(a)));
            titles.add("《" + a.getTitle() + "》");
        }
        return new ToolResult("相关文章 " + rows.size() + " 篇：" + String.join("、", titles),
                items(Map.of("items", rows)), sources);
    }

    // ------------------------------------------------------------------
    // list_taxonomy
    // ------------------------------------------------------------------

    private ToolResult listTaxonomy(Map<String, Object> args) {
        String type = StrUtil.blankToDefault(str(args, "type"), "category").trim().toLowerCase();
        List<Map<String, Object>> rows = new ArrayList<>(30);
        if ("tag".equals(type)) {
            for (Tag tag : articleService.listTags()) {
                if (rows.size() >= 30) {
                    break;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", tag.getName());
                row.put("count", tag.getArticleCount());
                rows.add(row);
            }
            return new ToolResult("共 " + rows.size() + " 个标签：" + names(rows), items(Map.of("items", rows)), List.of());
        }
        for (Category c : articleService.listCategories()) {
            if (rows.size() >= 30) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", c.getName());
            row.put("count", c.getArticleCount());
            rows.add(row);
        }
        return new ToolResult("共 " + rows.size() + " 个分类：" + names(rows), items(Map.of("items", rows)), List.of());
    }

    // ------------------------------------------------------------------
    // site_stats
    // ------------------------------------------------------------------

    private ToolResult siteStats() {
        Page<Article> latest = articleService.page(1, 1, null, null, null, null, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("articles", latest.getTotal());
        data.put("categories", articleService.listCategories().size());
        data.put("tags", articleService.listTags().size());
        LocalDateTime newest = latest.getRecords().isEmpty() ? null : latest.getRecords().get(0).getCreateTime();
        data.put("latestArticleAt", newest == null ? "" : newest.format(DAY));

        String digest = "本站共 " + data.get("articles") + " 篇文章、"
                + data.get("categories") + " 个分类、" + data.get("tags") + " 个标签"
                + (newest == null ? "" : "，最新一篇发布于 " + newest.format(DAY));
        return new ToolResult(digest, data, List.of());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** /post/{slug} 优先，与前端 articlePath() 的规则保持一致 */
    private String articleUrl(Article a) {
        return StrUtil.isNotBlank(a.getSlug()) ? "/post/" + a.getSlug() : "/article/" + a.getId();
    }

    private Map<String, Object> items(Map<String, Object> m) {
        return new LinkedHashMap<>(m);
    }

    private Map<String, Object> source(String type, String title, String url) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("type", type);
        s.put("title", title);
        s.put("url", url);
        return s;
    }

    private String names(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> String.valueOf(r.get("name"))).reduce((a, b) -> a + "、" + b).orElse("");
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> map = new LinkedHashMap<>();
        for (Category c : articleService.listCategories()) {
            map.put(c.getId(), c.getName());
        }
        return map;
    }

    /** 名称 → 分类 id，先精确匹配，再退化为忽略大小写的包含匹配 */
    private Long findCategoryId(String name) {
        List<Category> all = articleService.listCategories();
        for (Category c : all) {
            if (name.equals(c.getName()) || name.equalsIgnoreCase(c.getName())) {
                return c.getId();
            }
        }
        for (Category c : all) {
            if (c.getName() != null && c.getName().contains(name)) {
                return c.getId();
            }
        }
        return null;
    }

    private Long findTagId(String name) {
        List<Tag> all = articleService.listTags();
        for (Tag t : all) {
            if (name.equals(t.getName()) || name.equalsIgnoreCase(t.getName())) {
                return t.getId();
            }
        }
        for (Tag t : all) {
            if (t.getName() != null && t.getName().contains(name)) {
                return t.getId();
            }
        }
        return null;
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Long longArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int intArg(Map<String, Object> args, String key, int def) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(String.valueOf(v).trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
