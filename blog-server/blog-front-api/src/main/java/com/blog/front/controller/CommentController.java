package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.Comment;
import com.blog.common.entity.Article;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.CommentMapper;
import com.blog.common.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;

    @GetMapping("/list")
    public Result<Page<Comment>> list(@RequestParam Long articleId,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreateTime);
        return Result.ok(commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }

    @PostMapping
    public Result<Comment> create(@RequestBody Comment comment) {
        if (comment.getArticleId() == null || comment.getContent() == null || comment.getContent().isBlank()) {
            throw new BusinessException("参数不完整");
        }
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        comment.setUserId(userId);
        comment.setStatus(1);
        commentMapper.insert(comment);

        Article article = articleMapper.selectById(comment.getArticleId());
        if (article != null) {
            article.setCommentCount((article.getCommentCount() != null ? article.getCommentCount() : 0) + 1);
            articleMapper.updateById(article);
        }
        return Result.ok(comment);
    }
}
