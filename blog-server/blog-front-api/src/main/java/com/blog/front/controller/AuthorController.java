package com.blog.front.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.AuthorArticleDTO;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.exception.BusinessException;
import com.blog.front.cache.ArticleCacheKeys;
import com.blog.front.service.ArticleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作者（author）投稿后台。
 *
 * <p>这一套接口是**平行入口**，不跟 {@code ArticleController} 共用任何注解：
 * 那 37 个 {@code hasRole('admin')} 一个都不用改，作者天然进不去。
 * 一旦某个作者接口需要收窄，只改这一个类即可，不会牵连后台。
 *
 * <p>权限分三层：
 * <ol>
 *   <li>路径层：整类 {@code hasRole('author')}，普通用户和未登录都进不来；</li>
 *   <li>数据层：入参是 {@link AuthorArticleDTO}，里面**没有** status / isTop / isFeatured /
 *       userId / publishAt，伪造这些键也无处落地；</li>
 *   <li>服务层：{@code ArticleService} 再硬写一遍归属与状态，并做资源级归属校验。</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/author")
@PreAuthorize("hasRole('author')")
@RequiredArgsConstructor
public class AuthorController {

    private final ArticleService articleService;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 我的文章列表。status 可选：0 草稿 / 2 待审 / 3 已驳回 / 1 已发布 */
    @GetMapping("/articles")
    public Result<Page<Article>> list(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer status) {
        return Result.ok(articleService.authorPage(currentUserId(), pageNum, pageSize, keyword, status));
    }

    /**
     * 读自己的一篇（编辑页回填用）。
     *
     * <p>返回结构与公开详情接口对齐（{@code {article, tags}}），这样编辑器不用为作者模式
     * 单独写一套回填逻辑 —— 它已经在用 {@code res.data.article || res.data} 取。
     */
    @GetMapping("/articles/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Article article = articleService.getOwnArticle(id, currentUserId());
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("article", article);
        data.put("tags", articleService.getTagsByArticleId(article.getId()));
        return Result.ok(data);
    }

    /**
     * 新建。
     *
     * @param submit false=存草稿，true=直接提交审核。默认 false：
     *               默认值选"更安全"的那一侧，忘了传不会意外把文章推进审核队列。
     */
    @PostMapping("/articles")
    public Result<Article> create(@Valid @RequestBody AuthorArticleDTO dto,
                                  @RequestParam(defaultValue = "false") boolean submit) {
        Article created = articleService.createForAuthor(dto, currentUserId(), submit);
        evictAggCache();
        return Result.ok(created);
    }

    @PutMapping("/articles/{id}")
    public Result<Article> update(@PathVariable Long id,
                                  @Valid @RequestBody AuthorArticleDTO dto,
                                  @RequestParam(defaultValue = "false") boolean submit) {
        Article updated = articleService.updateForAuthor(id, dto, currentUserId(), submit);
        evictAggCache();
        return Result.ok(updated);
    }

    @DeleteMapping("/articles/{id}")
    public Result<?> delete(@PathVariable Long id) {
        articleService.deleteForAuthor(id, currentUserId());
        evictAggCache();
        return Result.ok();
    }

    /** 别名查重（编辑器实时校验）。给作者用的版本，语义与后台那个一致。 */
    @GetMapping("/slug-available")
    public Result<Map<String, Object>> slugAvailable(@RequestParam String slug,
                                                     @RequestParam(required = false) Long excludeId) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("available", articleService.slugAvailable(slug, excludeId));
        return Result.ok(result);
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }

    /**
     * 聚合缓存（归档 / 系列）只在"文章真正上线"时才会变，作者存草稿没必要清。
     * 这里保守清一次，代价极小，换来的是"发布后列表一定是最新的"。
     */
    private void evictAggCache() {
        try {
            redisTemplate.delete(List.of(ArticleCacheKeys.ARCHIVE, ArticleCacheKeys.SERIES));
        } catch (Exception ignored) {
            // 缓存清不掉不影响本次写入，最坏是列表多旧几十秒
        }
    }
}
