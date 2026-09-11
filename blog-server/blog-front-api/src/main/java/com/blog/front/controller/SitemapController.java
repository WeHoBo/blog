package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.entity.Article;
import com.blog.common.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SitemapController {

    /** 站点规范域名，与页面 canonical / og:url 保持一致 */
    private static final String BASE_URL = "https://codeup.asia";

    private final ArticleMapper articleMapper;

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
        sb.append("  <url>\n");
        sb.append("    <loc>").append(BASE_URL).append("/</loc>\n");
        sb.append("    <changefreq>daily</changefreq>\n");
        sb.append("    <priority>1.0</priority>\n");
        sb.append("  </url>\n");

        for (Article a : articles) {
            // 优先输出规范 URL /post/{slug}，与文章页 canonical 保持一致；
            // 没有 slug 的旧数据回退到 /article/{id}
            String path = (a.getSlug() != null && !a.getSlug().isBlank())
                    ? "/post/" + a.getSlug()
                    : "/article/" + a.getId();
            sb.append("  <url>\n");
            sb.append("    <loc>").append(BASE_URL).append(path).append("</loc>\n");
            var lastmod = a.getUpdateTime() != null ? a.getUpdateTime() : a.getCreateTime();
            if (lastmod != null) {
                sb.append("    <lastmod>").append(lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("</lastmod>\n");
            }
            sb.append("    <changefreq>weekly</changefreq>\n");
            sb.append("    <priority>0.8</priority>\n");
            sb.append("  </url>\n");
        }

        sb.append("</urlset>");
        return sb.toString();
    }
}
