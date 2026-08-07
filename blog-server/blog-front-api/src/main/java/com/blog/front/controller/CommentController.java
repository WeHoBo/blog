package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Comment;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CommentMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.common.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    @GetMapping("/list")
    public Result<Page<CommentVO>> list(@RequestParam Long articleId,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1)
                .orderByDesc(Comment::getCreateTime);
        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        
        List<Long> userIds = page.getRecords().stream()
                .map(Comment::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userIds.isEmpty() ? List.of() :
                userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Page<CommentVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        List<CommentVO> voList = new ArrayList<>();
        for (Comment c : page.getRecords()) {
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            vo.setArticleId(c.getArticleId());
            vo.setContent(c.getContent());
            vo.setCreateTime(c.getCreateTime());
            vo.setUserId(c.getUserId());
            User u = userMap.get(c.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname() != null ? u.getNickname() : u.getUsername());
                vo.setAvatar(u.getAvatar());
            }
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return Result.ok(voPage);
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
