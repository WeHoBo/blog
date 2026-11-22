package com.blog.front.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.blog.common.dto.Result;
import com.blog.common.entity.Article;
import com.blog.common.entity.Comment;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.CommentMapper;
import com.blog.common.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台删除用户的单元测试。
 *
 * <p>删除是这个类里唯一一个<b>不可恢复</b>的操作，所以测的不是覆盖率，
 * 而是把四条「必须拦住」的规则钉死：不能删自己、不能删超管、
 * 名下有文章不能删、名下有评论不能删 —— 以及「干净账号真的删得掉」。</p>
 */
class AdminUserControllerTest {

    /** LambdaQueryWrapper.eq(SFunction, …) 构造时要拿到列名，需要先建立实体的 TableInfo 缓存 */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        for (Class<?> type : List.of(User.class, Article.class, Comment.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), type);
        }
    }

    private final UserMapper userMapper = mock(UserMapper.class);
    private final ArticleMapper articleMapper = mock(ArticleMapper.class);
    private final CommentMapper commentMapper = mock(CommentMapper.class);

    private final AdminUserController controller =
            new AdminUserController(userMapper, articleMapper, commentMapper, mock(PasswordEncoder.class));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /** currentUserId() 从 principal 里取 Long，所以这里必须塞 Long 而不是 UserDetails */
    private void loginAs(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private User user(Long id, String role) {
        User u = new User();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }

    @Test
    void deleteMissingUser() {
        when(userMapper.selectById(9L)).thenReturn(null);
        BusinessException e = assertThrows(BusinessException.class, () -> controller.delete(9L, false));
        assertEquals("用户不存在", e.getMessage());
    }

    @Test
    void refuseDeleteSelf() {
        loginAs(3L);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "super"));

        BusinessException e = assertThrows(BusinessException.class, () -> controller.delete(3L, false));
        assertTrue(e.getMessage().contains("自己"), e.getMessage());
        verify(userMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void refuseDeleteSuper() {
        loginAs(3L);
        when(userMapper.selectById(1L)).thenReturn(user(1L, "super"));

        BusinessException e = assertThrows(BusinessException.class, () -> controller.delete(1L, false));
        assertTrue(e.getMessage().contains("超级管理员"), e.getMessage());
        verify(userMapper, never()).deleteById(any(Serializable.class));
    }

    /**
     * 有文章必须拦住 —— 否则 article.user_id 指向一个不存在的用户，
     * 首页文章列表 JOIN 不到作者，而且没有任何一处会报错。
     */
    @Test
    void refuseWhenUserHasArticles() {
        loginAs(3L);
        when(userMapper.selectById(5L)).thenReturn(user(5L, "author"));
        when(articleMapper.selectCount(any())).thenReturn(4L);

        BusinessException e = assertThrows(BusinessException.class, () -> controller.delete(5L, false));
        assertTrue(e.getMessage().contains("4 篇文章"), e.getMessage());
        // 提示里必须带上「改用禁用」这条路，否则站长只会反复点删除而没有出路
        assertTrue(e.getMessage().contains("禁用"), e.getMessage());
        verify(userMapper, never()).deleteById(any(Serializable.class));
        // 严格模式下即使算出了评论数，也不该真的去动 comment 表
        verify(commentMapper, never()).update(any(), any());
    }

    @Test
    void refuseWhenUserHasComments() {
        loginAs(3L);
        when(userMapper.selectById(6L)).thenReturn(user(6L, "user"));
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(commentMapper.selectCount(any())).thenReturn(7L);

        BusinessException e = assertThrows(BusinessException.class, () -> controller.delete(6L, false));
        assertTrue(e.getMessage().contains("7 条评论"), e.getMessage());
        verify(userMapper, never()).deleteById(any(Serializable.class));
    }

    /** 干净账号（没写文章、没留评论）应该真的删掉 */
    @Test
    void deleteCleanUser() {
        loginAs(3L);
        when(userMapper.selectById(8L)).thenReturn(user(8L, "user"));
        when(articleMapper.selectCount(any())).thenReturn(0L);
        when(commentMapper.selectCount(any())).thenReturn(0L);

        controller.delete(8L, false);

        verify(userMapper).deleteById(8L);
    }

    /**
     * 强制删除：文章必须转到操作人名下，否则会变成谁都管不了的无主文章；
     * 评论一行都不改 —— 前台列表在用户查不到时自然显示「匿名」，内容完整保留。
     */
    @Test
    void forceDeleteTransfersArticlesAndKeepsComments() {
        loginAs(3L);
        when(userMapper.selectById(5L)).thenReturn(user(5L, "author"));
        when(articleMapper.selectCount(any())).thenReturn(4L);
        when(commentMapper.selectCount(any())).thenReturn(7L);

        controller.delete(5L, true);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Article>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(articleMapper).update(any(), captor.capture());
        // 断言的列名而不是「调用了 update」：改成 set(status) 这种错写法也算通过就没意义了
        assertTrue(((LambdaUpdateWrapper<Article>) captor.getValue()).getSqlSet().contains("user_id"),
                ((LambdaUpdateWrapper<Article>) captor.getValue()).getSqlSet());

        // 评论表一个字都不能动（转到站长名下等于冒名发言）
        verify(commentMapper, never()).update(any(), any());
        verify(commentMapper, never()).deleteById(any(Serializable.class));
        verify(userMapper).deleteById(5L);
    }

    /** force 不是后门：删自己、删超管，加了 force 也照样拒 */
    @Test
    void forceCannotBypassSelfAndSuperGuards() {
        loginAs(3L);
        when(userMapper.selectById(3L)).thenReturn(user(3L, "super"));
        assertThrows(BusinessException.class, () -> controller.delete(3L, true));

        when(userMapper.selectById(1L)).thenReturn(user(1L, "super"));
        assertThrows(BusinessException.class, () -> controller.delete(1L, true));

        verify(userMapper, never()).deleteById(any(Serializable.class));
    }

    /** 预检接口：删除确认框要靠它说准数量 */
    @Test
    void refsReturnsCounts() {
        when(userMapper.selectById(6L)).thenReturn(user(6L, "user"));
        when(articleMapper.selectCount(any())).thenReturn(2L);
        when(commentMapper.selectCount(any())).thenReturn(9L);

        Result<Map<String, Long>> res = controller.refs(6L);

        assertEquals(2L, res.getData().get("articles"));
        assertEquals(9L, res.getData().get("comments"));
    }
}
