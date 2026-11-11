package com.blog.front.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.blog.common.dto.CommentReportForm;
import com.blog.common.entity.Article;
import com.blog.common.entity.Comment;
import com.blog.common.entity.CommentReport;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CommentMapper;
import com.blog.common.mapper.CommentReportMapper;
import com.blog.common.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论举报 + 管理端删除的单元测试。
 *
 * <p>重点不是覆盖率，而是把「几条容易写错的规则」钉住：
 * 原因必须在白名单里、不能举报自己、重复举报要给中文提示、删评论要回填文章评论数。</p>
 */
class CommentAdminControllerTest {

    /**
     * LambdaUpdateWrapper.set(SFunction, …) 在构造时就要拿到列名，
     * 需要先让 MyBatis-Plus 建立实体的 TableInfo 缓存，否则报
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> type : List.of(Comment.class, Article.class, CommentReport.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), type);
        }
    }

    private final CommentMapper commentMapper = mock(CommentMapper.class);
    private final CommentReportMapper reportMapper = mock(CommentReportMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    private final CommentController commentController =
            new CommentController(commentMapper, reportMapper, articleMapper, userMapper, null);

    private final CommentAdminController adminController =
            new CommentAdminController(commentMapper, reportMapper, articleMapper, userMapper);

    private void loginAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Comment comment(long id, long userId) {
        Comment c = new Comment();
        c.setId(id);
        c.setArticleId(108L);
        c.setUserId(userId);
        c.setContent("测试评论");
        c.setStatus(1);
        return c;
    }

    @Test
    void reportRejectsUnknownReason() {
        loginAs(1L);
        CommentReportForm form = new CommentReportForm();
        form.setCommentId(9L);
        form.setReason("我看他不爽");

        BusinessException e = assertThrows(BusinessException.class, () -> commentController.report(form));
        assertEquals("举报原因不合法", e.getMessage());
        verify(reportMapper, never()).insert(any(CommentReport.class));
    }

    @Test
    void reportRejectsOwnComment() {
        loginAs(7L);
        when(commentMapper.selectById(9L)).thenReturn(comment(9L, 7L));

        CommentReportForm form = new CommentReportForm();
        form.setCommentId(9L);
        form.setReason("垃圾广告");

        BusinessException e = assertThrows(BusinessException.class, () -> commentController.report(form));
        assertEquals("不能举报自己的评论", e.getMessage());
    }

    @Test
    void reportRejectsDeletedComment() {
        loginAs(1L);
        Comment deleted = comment(9L, 7L);
        deleted.setStatus(0);
        when(commentMapper.selectById(9L)).thenReturn(deleted);

        CommentReportForm form = new CommentReportForm();
        form.setCommentId(9L);
        form.setReason("垃圾广告");

        assertThrows(BusinessException.class, () -> commentController.report(form));
    }

    @Test
    void reportSavesPendingRecord() {
        loginAs(1L);
        when(commentMapper.selectById(9L)).thenReturn(comment(9L, 7L));

        CommentReportForm form = new CommentReportForm();
        form.setCommentId(9L);
        form.setReason("辱骂攻击");
        commentController.report(form);

        ArgumentCaptor<CommentReport> captor = ArgumentCaptor.forClass(CommentReport.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getCommentId());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals("辱骂攻击", captor.getValue().getReason());
        assertEquals(0, captor.getValue().getStatus());
    }

    @Test
    void reportReturnsFriendlyMessageOnDuplicate() {
        loginAs(1L);
        when(commentMapper.selectById(9L)).thenReturn(comment(9L, 7L));
        when(reportMapper.insert(any(CommentReport.class))).thenThrow(new DuplicateKeyException("uk_comment_user"));

        CommentReportForm form = new CommentReportForm();
        form.setCommentId(9L);
        form.setReason("垃圾广告");

        BusinessException e = assertThrows(BusinessException.class, () -> commentController.report(form));
        assertEquals("你已经举报过这条评论，我们会尽快处理", e.getMessage());
    }

    @Test
    void deleteRecomputesArticleCommentCount() {
        when(commentMapper.selectById(9L)).thenReturn(comment(9L, 7L));
        when(commentMapper.selectCount(any(Wrapper.class))).thenReturn(4L);

        adminController.delete(9L);

        verify(commentMapper).deleteById(9L);
        // 先重算剩余条数，再把结果写回 article.comment_count（不是 ±1 猜的）
        verify(commentMapper).selectCount(any(Wrapper.class));
        verify(articleMapper).update(isNull(), any(Wrapper.class));
        // 该评论名下的举报一并关闭
        verify(reportMapper).update(isNull(), any(Wrapper.class));
    }

    @Test
    void deleteMissingCommentFails() {
        when(commentMapper.selectById(404L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> adminController.delete(404L));
        assertEquals("评论不存在或已删除", e.getMessage());
    }

    @Test
    void ignoreReportDoesNotTouchComment() {
        CommentReport report = new CommentReport();
        report.setId(3L);
        report.setCommentId(9L);
        report.setStatus(0);
        when(reportMapper.selectById(3L)).thenReturn(report);

        adminController.handleReport(3L, java.util.Map.of("action", "ignore"));

        verify(commentMapper, never()).deleteById(anyLong());
        verify(reportMapper).update(isNull(), any(Wrapper.class));
    }
}
