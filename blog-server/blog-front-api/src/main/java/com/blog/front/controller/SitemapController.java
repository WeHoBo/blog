package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import com.blog.common.vo.CategoryArticleCount;
import com.blog.common.vo.TagArticleCount;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    /** 站点规范域名，与页面 canonical / og:url 保持一致 */
    private static final String BASE_URL = "https://codeup.asia";

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        List<Article> articles = articleMapper.selectList(
                new LambdaQueryWrapper<Article>()
                        .eq(Article::getStatus, 1)
                        .eq(Article::getIsDeleted, 0)
                        .orderByDesc(Article::getCreateTime)
                        .last("LIMIT 5000"));

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 首页
        appendUrl(sb, "/", "daily", "1.0", null);

        // 文章：优先输出规范 URL /post/{slug}，与文章页 canonical 保持一致；
        // 没有 slug 的旧数据回退到 /article/{id}
        for (Article a : articles) {
            String path = (a.getSlug() != null && !a.getSlug().isBlank())
                    ? "/post/" + encode(a.getSlug())
                    : "/article/" + a.getId();
            var lastmod = a.getUpdateTime() != null ? a.getUpdateTime() : a.getCreateTime();
            appendUrl(sb, path, "weekly", "0.8",
                    lastmod != null ? lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE) : null);
        }

        // ---------------------------------------------------------------
        // 聚合页（标签云 / 分类 / 各标签与分类的落地页）
        // 之前这些页面没有独立 URL，也就无从进 sitemap；现在补齐。
        // 只输出「确实有已发布文章」的标签/分类：空聚合页属于低质内容，不该被收录。
        // ---------------------------------------------------------------
        appendUrl(sb, "/tags", "weekly", "0.5", null);
        appendUrl(sb, "/categories", "weekly", "0.5", null);

        Set<Long> tagIdsWithArticles = new HashSet<>();
        for (TagArticleCount row : articleTagMapper.countPublishedByTag()) {
            if (row.getTagId() != null && row.getCnt() != null && row.getCnt() > 0) {
                tagIdsWithArticles.add(row.getTagId());
            }
        }
        for (Tag t : tagMapper.selectList(null)) {
            if (t.getId() == null || !tagIdsWithArticles.contains(t.getId())) {
                continue;
            }
            appendUrl(sb, "/tag/" + encode(taxonomyKey(t.getSlug(), t.getId())), "weekly", "0.5", null);
        }

        Set<Long> categoryIdsWithArticles = new HashSet<>();
        for (CategoryArticleCount row : articleMapper.countPublishedByCategory()) {
            if (row.getCategoryId() != null && row.getCnt() != null && row.getCnt() > 0) {
                categoryIdsWithArticles.add(row.getCategoryId());
            }
        }
        for (Category c : categoryMapper.selectList(null)) {
            if (c.getId() == null || !categoryIdsWithArticles.contains(c.getId())) {
                continue;
            }
            appendUrl(sb, "/category/" + encode(taxonomyKey(c.getSlug(), c.getId())), "weekly", "0.5", null);
        }

        sb.append("</urlset>");
        return sb.toString();
    }

    /**
     * 生成一条 &lt;url&gt;。
     * 抽出来是因为聚合页让条目数量翻了数倍，再逐个手工 append 极易漏掉标签闭合。
     */
    private void appendUrl(StringBuilder sb, String path, String changefreq, String priority, String lastmod) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(BASE_URL).append(path).append("</loc>\n");
        if (lastmod != null) {
            sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    /**
     * slug 优先、为空则退回 id —— 与前端 utils/taxonomy.ts 的 taxonomyPath() 规则一致，
     * 两边不一致会让 sitemap 里的 URL 与页面 canonical 对不上。
     */
    private String taxonomyKey(String slug, Long id) {
        return (slug != null && !slug.isBlank()) ? slug.trim() : String.valueOf(id);
    }

    /** slug 可能是中文，必须百分号编码后才是一个合法 URL */
    private String encode(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
