package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import com.blog.common.vo.CommentAdminVO;
import com.blog.common.vo.CommentReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端评论：列表 / 删除 / 举报处理。
 *
 * <p>为什么单独一个 Controller：类上直接挂 {@code @PreAuthorize("hasRole('admin')")}，
 * 不会出现「新加一个方法忘了加注解」导致越权的情况。</p>
 */
@RestController
@RequestMapping("/api/comment/admin")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class CommentAdminController {

    private final CommentMapper commentMapper;
    private final CommentReportMapper commentReportMapper;
    private final ArticleMapper articleMapper;
    private final UserMapper userMapper;

    /**
     * 评论列表。
     *
     * @param keyword   按评论正文模糊查
     * @param articleId 只看某篇文章
     * @param reported  true = 只看「被举报过」的评论（优先处理用）
     */
    @GetMapping("/list")
    public Result<Page<CommentAdminVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long articleId,
            @RequestParam(required = false) Boolean reported) {

        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (articleId != null) {
            wrapper.eq(Comment::getArticleId, articleId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Comment::getContent, keyword.trim());
        }
        if (Boolean.TRUE.equals(reported)) {
            List<Long> reportedIds = commentReportMapper.selectList(
                            new LambdaQueryWrapper<CommentReport>().select(CommentReport::getCommentId))
                    .stream().map(CommentReport::getCommentId).distinct().collect(Collectors.toList());
            if (reportedIds.isEmpty()) {
                // 没有举报时直接返回空页，别让 in() 生成 `IN ()` 这种非法 SQL
                return Result.ok(new Page<>(pageNum, pageSize, 0));
            }
            wrapper.in(Comment::getId, reportedIds);
        }
        wrapper.orderByDesc(Comment::getCreateTime);

        Page<Comment> page = commentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Comment> rows = page.getRecords();

        Page<CommentAdminVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        if (rows.isEmpty()) {
            voPage.setRecords(List.of());
            return Result.ok(voPage);
        }

        List<Long> commentIds = rows.stream().map(Comment::getId).collect(Collectors.toList());
        List<Long> articleIds = rows.stream().map(Comment::getArticleId).distinct().collect(Collectors.toList());
        List<Long> userIds = rows.stream().map(Comment::getUserId).distinct().collect(Collectors.toList());

        // 只取标题，避免把正文 LONGTEXT 一起读出来
        Map<Long, String> titleMap = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getTitle)
                        .in(Article::getId, articleIds))
                .stream().collect(Collectors.toMap(Article::getId, Article::getTitle, (a, b) -> a));

        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 一次把这一页的举报查出来，内存里分组，别在循环里查库
        Map<Long, int[]> reportStat = new HashMap<>();
        for (CommentReport r : commentReportMapper.selectList(new LambdaQueryWrapper<CommentReport>()
                .select(CommentReport::getCommentId, CommentReport::getStatus)
                .in(CommentReport::getCommentId, commentIds))) {
            int[] stat = reportStat.computeIfAbsent(r.getCommentId(), k -> new int[2]);
            stat[0]++;
            if (r.getStatus() != null && r.getStatus() == 0) {
                stat[1]++;
            }
        }

        List<CommentAdminVO> voList = new ArrayList<>();
        for (Comment c : rows) {
            CommentAdminVO vo = new CommentAdminVO();
            vo.setId(c.getId());
            vo.setArticleId(c.getArticleId());
            vo.setArticleTitle(titleMap.getOrDefault(c.getArticleId(), "（文章已删除）"));
            vo.setUserId(c.getUserId());
            User u = userMap.get(c.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname() != null ? u.getNickname() : u.getUsername());
                vo.setAvatar(u.getAvatar());
            }
            vo.setContent(c.getContent());
            vo.setStatus(c.getStatus());
            vo.setCreateTime(c.getCreateTime());
            int[] stat = reportStat.get(c.getId());
            vo.setReportCount(stat == null ? 0 : stat[0]);
            vo.setPendingReportCount(stat == null ? 0 : stat[1]);
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return Result.ok(voPage);
    }

    /** 删除评论（物理删除）并回填文章评论数、关闭该评论下所有举报 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        deleteComment(id);
        return Result.ok();
    }

    /** 举报列表；status 不传 = 全部（0 待处理 / 1 已忽略 / 2 已删除评论） */
    @GetMapping("/reports")
    public Result<Page<CommentReportVO>> reports(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer status) {

        LambdaQueryWrapper<CommentReport> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CommentReport::getStatus, status);
        }
        wrapper.orderByDesc(CommentReport::getCreateTime);
        Page<CommentReport> page = commentReportMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        Page<CommentReportVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        List<CommentReport> rows = page.getRecords();
        if (rows.isEmpty()) {
            voPage.setRecords(List.of());
            return Result.ok(voPage);
        }

        List<Long> commentIds = rows.stream().map(CommentReport::getCommentId).distinct().collect(Collectors.toList());
        List<Long> reporterIds = rows.stream().map(CommentReport::getUserId).distinct().collect(Collectors.toList());

        Map<Long, Comment> commentMap = commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(Comment::getId, c -> c, (a, b) -> a));
        List<Long> articleIds = commentMap.values().stream()
                .map(Comment::getArticleId).distinct().collect(Collectors.toList());
        Map<Long, String> titleMap = articleIds.isEmpty() ? Map.of()
                : articleMapper.selectList(new LambdaQueryWrapper<Article>()
                        .select(Article::getId, Article::getTitle)
                        .in(Article::getId, articleIds))
                .stream().collect(Collectors.toMap(Article::getId, Article::getTitle, (a, b) -> a));
        Map<Long, User> reporterMap = userMapper.selectBatchIds(reporterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<CommentReportVO> voList = new ArrayList<>();
        for (CommentReport r : rows) {
            CommentReportVO vo = new CommentReportVO();
            vo.setId(r.getId());
            vo.setCommentId(r.getCommentId());
            vo.setReason(r.getReason());
            vo.setStatus(r.getStatus());
            vo.setCreateTime(r.getCreateTime());
            vo.setReporterId(r.getUserId());
            User reporter = reporterMap.get(r.getUserId());
            if (reporter != null) {
                vo.setReporterNickname(reporter.getNickname() != null ? reporter.getNickname() : reporter.getUsername());
            }
            Comment c = commentMap.get(r.getCommentId());
            if (c != null) {
                vo.setCommentContent(c.getContent());
                vo.setCommentStatus(c.getStatus());
                vo.setArticleId(c.getArticleId());
                vo.setArticleTitle(titleMap.getOrDefault(c.getArticleId(), "（文章已删除）"));
            } else {
                vo.setArticleTitle("（评论已删除）");
            }
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return Result.ok(voPage);
    }

    /**
     * 处理举报。
     *
     * <p>action = ignore：只把这条举报标记为已忽略，评论保留；
     * action = delete：删掉评论，并把该评论下所有举报一起标记为「已删除」。</p>
     */
    @PutMapping("/reports/{id}")
    public Result<?> handleReport(@PathVariable Long id, @RequestBody Map<String, String> body) {
        CommentReport report = commentReportMapper.selectById(id);
        if (report == null) {
            throw new BusinessException("举报不存在");
        }
        String action = body == null ? null : body.get("action");
        if ("ignore".equals(action)) {
            commentReportMapper.update(null, new LambdaUpdateWrapper<CommentReport>()
                    .set(CommentReport::getStatus, 1)
                    .eq(CommentReport::getId, id));
        } else if ("delete".equals(action)) {
            deleteComment(report.getCommentId());
        } else {
            throw new BusinessException("未知操作");
        }
        return Result.ok();
    }

    /** 删除评论的公共逻辑：删评论 → 回填文章评论数 → 关闭其名下所有举报 */
    private void deleteComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在或已删除");
        }
        commentMapper.deleteById(commentId);

        Long remain = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, comment.getArticleId())
                .eq(Comment::getStatus, 1));
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .set(Article::getCommentCount, remain == null ? 0 : remain.intValue())
                .eq(Article::getId, comment.getArticleId()));

        commentReportMapper.update(null, new LambdaUpdateWrapper<CommentReport>()
                .set(CommentReport::getStatus, 2)
                .eq(CommentReport::getCommentId, commentId));
    }
}
