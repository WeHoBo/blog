package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.constant.CommentReportReasons;
import com.blog.common.dto.CommentReportForm;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Comment;
import com.blog.common.entity.CommentReport;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CommentMapper;
import com.blog.common.mapper.CommentReportMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.common.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
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
    private final CommentReportMapper commentReportMapper;
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

    /**
     * 举报评论。
     *
     * <p>只记录「谁、哪条、什么原因」，不复制正文；真正删不删由管理员在后台决定。
     * 同一个人对同一条评论只能举报一次（唯一键 uk_comment_user 兜住并发）。</p>
     */
    @PostMapping("/report")
    public Result<?> report(@RequestBody CommentReportForm form) {
        if (form == null || form.getCommentId() == null) {
            throw new BusinessException("参数不完整");
        }
        String reason = form.getReason() == null ? "" : form.getReason().trim();
        if (!CommentReportReasons.ALL.contains(reason)) {
            throw new BusinessException("举报原因不合法");
        }
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Comment comment = commentMapper.selectById(form.getCommentId());
        if (comment == null || comment.getStatus() == null || comment.getStatus() != 1) {
            throw new BusinessException("评论不存在或已删除");
        }
        if (userId.equals(comment.getUserId())) {
            throw new BusinessException("不能举报自己的评论");
        }

        CommentReport report = new CommentReport();
        report.setCommentId(form.getCommentId());
        report.setUserId(userId);
        report.setReason(reason);
        report.setStatus(0);
        try {
            commentReportMapper.insert(report);
        } catch (DuplicateKeyException e) {
            // 唯一键冲突：已举报过。给一句人话，不要把 SQL 异常抛给前端
            throw new BusinessException("你已经举报过这条评论，我们会尽快处理");
        }
        return Result.ok();
    }
}
