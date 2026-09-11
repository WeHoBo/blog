package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/comment")
@RequiredArgsConstructor
public class CommentController {

    /** 与前端输入框上限保持一致，服务端强制校验，避免超长内容入库 */
    private static final int MAX_CONTENT_LENGTH = 500;

    /** 同一用户两次评论的最小间隔（秒），用于基础防刷 */
    private static final long COMMENT_INTERVAL_SECONDS = 10;

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
        String content = comment.getContent().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("评论内容不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 基础防刷：同一用户对同一篇文章 N 秒内只允许发一条（setIfAbsent + TTL 为原子操作）
        String rateKey = "comment:rate:" + userId + ":" + comment.getArticleId();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(rateKey, "1", Duration.ofSeconds(COMMENT_INTERVAL_SECONDS));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException("评论太频繁了，请稍后再试");
        }

        // 只取校验所需的最小列，避免把文章正文一并读出来
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getStatus)
                .eq(Article::getId, comment.getArticleId()));
        if (article == null || article.getStatus() == null || article.getStatus() != 1) {
            throw new BusinessException("文章不存在或不可评论");
        }

        // 仅落库必要字段，防止客户端伪造 id / createTime / userId / status
        Comment entity = new Comment();
        entity.setArticleId(comment.getArticleId());
        entity.setContent(content);
        entity.setUserId(userId);
        entity.setStatus(1);
        commentMapper.insert(entity);

        // 原子自增，避免「读-改-写」在并发下丢失计数
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .setSql("comment_count = comment_count + 1")
                .eq(Article::getId, comment.getArticleId()));

        return Result.ok(entity);
    }
}
