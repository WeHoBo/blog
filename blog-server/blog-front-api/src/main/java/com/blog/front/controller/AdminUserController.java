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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户管理：改角色 / 改密码 / 禁用账号。
 *
 * <p><b>整类只给超管</b>（{@code hasRole('super')}）。这是超级管理员和普通管理员的核心分界线 ——
 * 能进这个页面，就等于能把任何一个账号提成管理员、也能改别人密码，
 * 所以它不能和「写文章」同级别。
 *
 * <p>类里另有四道防呆，都是为了防止「手滑把自己锁死」：<br>
 * ① 角色只能在 admin / user 之间切，super 只能走 SQL 授予（否则后台上点多两下就多一个超管）；<br>
 * ② 不能改自己的角色 / 状态（一降级就再也进不来，且没人是超管了）；<br>
 * ③ 不能动另一个 super（理论上只有一个，但数据被人手工写脏时别让它继续烂下去）；<br>
 * ④ 删除：不能删自己、不能删 super、名下还有文章或评论的一律拒绝（见 {@link #delete(Long)}）。
 */
@RestController
@RequestMapping("/api/admin/user")
@PreAuthorize("hasRole('super')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword).or().like(User::getNickname, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        page.getRecords().forEach(u -> u.setPassword(null)); // 脱敏
        return Result.ok(page);
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody User form) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");

        Long me = currentUserId();
        boolean isSelf = me != null && me.equals(id);
        boolean targetIsSuper = "super".equals(user.getRole());

        // 只有"真的要改"才校验：前端每次都把整个表单发上来，值没变就不该拦
        if (form.getRole() != null && !form.getRole().trim().equals(user.getRole())) {
            String role = form.getRole().trim();
            // author 也允许：超管要能手工收回某个人的投稿权限（比如长期不写、或者乱投）。
            // super 依旧不在白名单里 —— 它只能靠 sql/super-admin.sql 授予。
            if (!"admin".equals(role) && !"user".equals(role) && !"author".equals(role)) {
                throw new BusinessException(400, "角色只能是 user / author / admin；超级管理员请在数据库里维护");
            }
            if (isSelf) throw new BusinessException(400, "不能修改自己的角色（会把自己锁在门外）");
            if (targetIsSuper) throw new BusinessException(400, "不能修改超级管理员的角色");
            user.setRole(role);
        }

        if (form.getStatus() != null && !form.getStatus().equals(user.getStatus())) {
            if (isSelf) throw new BusinessException(400, "不能禁用自己的账号");
            if (targetIsSuper) throw new BusinessException(400, "不能禁用超级管理员");
            user.setStatus(form.getStatus());
        }

        if (form.getNickname() != null) user.setNickname(form.getNickname());
        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        userMapper.updateById(user);
        return Result.ok();
    }

    /**
     * 删除账号（物理删除）。
     *
     * <p><b>为什么默认拒绝「有关联内容」的用户，而不是级联删除</b>：<br>
     * {@code article.user_id} / {@code comment.user_id} 没有外键约束，直接删人会留下一堆
     * 指向不存在用户的孤儿数据 —— 首页文章列表查不到作者、评论区头像昵称全空白，
     * 而且没有任何一处会报错，属于「删完才发现站烂了」的那类事故。
     * 级联删除更糟：删一个读者顺手带走他的评论，删一个作者顺手带走他所有文章，且不可恢复。
     *
     * <p>所以默认「先拦住、把数量告诉你」，让站长自己决定是清内容还是改用「禁用」。
     * 绝大多数场景想要的其实只是「让这个人进不来」，那是 {@code status=0}，不是删行。
     *
     * <p><b>{@code force=true} 时的处理（站长确认过「保留内容」）</b>：
     * <ul>
     *   <li>文章：{@code user_id} 批量改成<b>当前操作人</b>。文章是资产，必须有人持有 ——
     *       留着悬空 id 会让首页列表查不到作者、后台按作者筛出「无主文章」。</li>
     *   <li>评论：<b>一行都不改</b>。前台评论列表是「先查评论、再按 id 批量查用户」拼出来的
     *       （{@code CommentController.list}），用户不存在时昵称/头像自然为空，
     *       前端已经兜底成「匿名」。内容完整保留、也不会被误认成站长说的话 ——
     *       相比之下把评论转到站长名下才是真问题（等于冒名发言）。</li>
     * </ul>
     * 这两者处理方式不同不是随意：文章侧没有降级显示（作者空白），评论侧有。
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @RequestParam(defaultValue = "false") boolean force) {
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException("用户不存在");

        Long me = currentUserId();
        if (me != null && me.equals(id)) {
            throw new BusinessException(400, "不能删除自己的账号");
        }
        if ("super".equals(user.getRole())) {
            throw new BusinessException(400, "不能删除超级管理员");
        }

        long articles = articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getUserId, id));
        long comments = commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, id));

        if (!force) {
            if (articles > 0) {
                throw new BusinessException(400, "该用户名下有 " + articles
                        + " 篇文章，请先删除或转移后再删账号；只想让人进不来的话，用「禁用」即可");
            }
            if (comments > 0) {
                throw new BusinessException(400, "该用户名下有 " + comments
                        + " 条评论，请先在「评论」里清理后再删账号；只想让人进不来的话，用「禁用」即可");
            }
            userMapper.deleteById(id);
            return Result.ok();
        }

        // 强制删除：文章必须有归属，否则会变成谁都管不了的无主文章。
        // me 为空说明拿不到登录态（正常不会发生，@PreAuthorize 已经挡住），此时宁可不删。
        if (articles > 0) {
            if (me == null) {
                throw new BusinessException(500, "无法确定当前登录用户，已中止转移文章");
            }
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getUserId, id)
                    .set(Article::getUserId, me));
        }
        userMapper.deleteById(id);

        Map<String, Long> moved = new LinkedHashMap<>();
        moved.put("articles", articles);
        moved.put("comments", comments);
        return Result.ok(moved);
    }

    /**
     * 删除前预检：这个用户名下有多少文章、多少评论。
     *
     * <p>单独开一个接口而不是塞进列表，是因为列表一次 100 行，
     * 每行都数一遍文章和评论就是 200 次 count —— 点开删除时再查一次，成本低得多，
     * 也能让确认框说出准确数字（而不是让站长先点一次失败、看报错猜数量）。
     */
    @GetMapping("/{id}/refs")
    public Result<Map<String, Long>> refs(@PathVariable Long id) {
        if (userMapper.selectById(id) == null) throw new BusinessException("用户不存在");
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("articles", articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getUserId, id)));
        counts.put("comments", commentMapper.selectCount(
                new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, id)));
        return Result.ok(counts);
    }

    /** 当前登录用户 id（JwtAuthenticationFilter 把 userId 放进了 principal） */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) return id;
        return null;
    }
}
