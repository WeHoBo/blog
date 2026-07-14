package com.blog.front.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.front.service.ArticleService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/list")
    public Result<Page<Article>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(articleService.page(pageNum, pageSize, categoryId, tagId, keyword));
    }

    @GetMapping("/{id:\\d+}")
    public Result<Article> detail(@PathVariable Long id) {
        return Result.ok(articleService.getById(id));
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
        return Result.ok(articleService.create(dto, userId));
    }

    @PreAuthorize("hasRole('admin')")
    @PutMapping("/{id}")
    public Result<Article> update(@PathVariable Long id, @Valid @RequestBody ArticleDTO dto) {
        return Result.ok(articleService.update(id, dto));
    }

    @PreAuthorize("hasRole('admin')")
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        articleService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('admin')")
    @PostMapping("/import")
    public Result<Article> importMd(@RequestParam("file") MultipartFile file) throws IOException {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return Result.ok(articleService.importMd(file, userId));
    }

    @GetMapping("/{id}/export")
    public void exportMd(@PathVariable Long id, HttpServletResponse response) throws IOException {
        Article article = articleService.getById(id);
        String content = article.getContentMd() != null ? article.getContentMd() : "";
        String filename = article.getTitle() + ".md";
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" +
                URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
    }
}
