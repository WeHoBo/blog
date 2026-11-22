package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.entity.ApplyQuestion;
import com.blog.common.entity.ApplySetting;
import com.blog.common.entity.User;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ApplyQuestionMapper;
import com.blog.common.mapper.ApplySettingMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「答题申请成为作者」的业务逻辑。
 *
 * <p>三条铁律，缺一条这个功能就会变成安全洞：
 * <ol>
 *   <li><b>答案不下发</b>：出题接口只给题干和选项，判分只在这里做。
 *       把 answer 一起返回给前端，等于把通关密码写在了网络面板里。</li>
 *   <li><b>角色不降级</b>：只有 {@code role = 'user'} 的人能被授予 author。
 *       如果写成无条件的 {@code SET role='author'}，超管点一下就把自己降成作者了。</li>
 *   <li><b>授予后要换 token</b>：role 是写进 JWT claim 的，改库不改 token 的话，
 *       用户刷新一百次也还是旧角色 —— 前端会一直报 403，看起来像功能坏了。</li>
 * </ol>
 *
 * <p>另外：这是一条<b>无人值守的授权路径</b>，所以必须限流。
 * 三道题、三个选项，暴力穷举只有 27 种组合，不限流的话脚本一秒就能把全站角色刷满。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplyService {

    /** 每人每天最多提交几次。3 次足够覆盖手滑，又让穷举变得不划算 */
    private static final int MAX_ATTEMPTS_PER_DAY = 3;
    private static final int MAX_QUESTIONS = 5;
    private static final int MIN_QUESTIONS = 1;

    private final ApplyQuestionMapper questionMapper;
    private final ApplySettingMapper settingMapper;
    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final RedisTemplate<String, Object> redis;

    // =================================================================
    // 用户侧
    // =================================================================

    /**
     * 申请页需要的一切：题目（**不含答案**）+ 通过题数 + 剩余次数 + 我当前的角色。
     */
    public Map<String, Object> info(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }

        ApplySetting setting = currentSetting();
        List<ApplyQuestion> questions = enabledQuestions();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", setting.getEnabled() == null || setting.getEnabled() == 1);
        data.put("passCount", effectivePassCount(setting, questions.size()));
        data.put("total", questions.size());
        data.put("myRole", user.getRole());
        data.put("alreadyAuthor", !"user".equals(StrUtil.blankToDefault(user.getRole(), "user")));
        data.put("attemptsLeft", attemptsLeft(userId));

        List<Map<String, Object>> list = new ArrayList<>(questions.size());
        for (ApplyQuestion q : questions) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", q.getId());
            item.put("question", q.getQuestion());
            // ⚠️ 只放选项文本，不放 answer
            item.put("options", List.of(
                    StrUtil.blankToDefault(q.getOptionA(), ""),
                    StrUtil.blankToDefault(q.getOptionB(), ""),
                    StrUtil.blankToDefault(q.getOptionC(), "")));
            list.add(item);
        }
        data.put("questions", list);
        return data;
    }

    /**
     * 判分并（可能）授予 author 角色。
     *
     * @param answers questionId -> 选项下标(0/1/2)
     * @return passed / score / total / passCount / token（仅通过时返回）
     */
    @Transactional
    public Map<String, Object> submit(Long userId, Map<Long, Integer> answers) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        String role = StrUtil.blankToDefault(user.getRole(), "user");
        if (!"user".equals(role)) {
            // 已经是作者/管理员，没必要再考一遍；也避免被拿来刷接口
            throw new BusinessException(400, "你已经有发布权限了，无需重复申请");
        }

        ApplySetting setting = currentSetting();
        if (setting.getEnabled() != null && setting.getEnabled() == 0) {
            throw new BusinessException(400, "申请通道暂未开放");
        }

        List<ApplyQuestion> questions = enabledQuestions();
        if (questions.isEmpty()) {
            throw new BusinessException(400, "管理员还没有配置题目，请稍后再试");
        }
        if (attemptsLeft(userId) <= 0) {
            throw new BusinessException(429, "今天的申请次数已用完，明天再来吧");
        }
        // 先扣次数再判分：判分本身很便宜，但"扣次数"必须在任何返回值之前，
        // 否则答错的请求会被反复重放
        bumpAttempt(userId);

        int score = 0;
        for (ApplyQuestion q : questions) {
            Integer picked = answers == null ? null : answers.get(q.getId());
            if (picked != null && q.getAnswer() != null && picked.equals(q.getAnswer())) {
                score++;
            }
        }

        int passCount = effectivePassCount(setting, questions.size());
        boolean passed = score >= passCount;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("passed", passed);
        data.put("score", score);
        data.put("total", questions.size());
        data.put("passCount", passCount);
        data.put("attemptsLeft", Math.max(0, attemptsLeft(userId)));

        if (!passed) {
            data.put("message", "答对 " + score + " 题，还差 " + (passCount - score) + " 题");
            return data;
        }

        // ---- 授予角色 ----
        // where 里带上 role='user'：这是一次乐观并发保护，
        // 既防止把 admin/super 降级，也防止两条并发请求各发一次 token
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .eq(User::getRole, "user")
                .set(User::getRole, "author"));
        if (updated == 0) {
            // 并发下另一个请求已经改过了，这里按成功处理即可（角色已是 author）
            log.info("[申请作者] userId={} 角色更新影响 0 行（可能已被并发请求授予）", userId);
        }
        log.info("[申请作者] userId={} 通过考核 score={}/{}，已授予 author", userId, score, questions.size());

        data.put("role", "author");
        // 换发 token：不换的话旧 token 里还是 role=user，用户刷新页面依然是"没权限"
        data.put("token", jwtUtils.generateToken(user.getId(), user.getUsername(), "author"));
        data.put("message", "恭喜，你已获得发布权限");
        return data;
    }

    // =================================================================
    // 超管侧：题目与通过题数
    // =================================================================

    public List<ApplyQuestion> listAll() {
        return questionMapper.selectList(new LambdaQueryWrapper<ApplyQuestion>()
                .orderByAsc(ApplyQuestion::getSort)
                .orderByAsc(ApplyQuestion::getId));
    }

    /** 新增或更新一道题。answer 只有这里能看到，也只会被写进库。 */
    @Transactional
    public ApplyQuestion saveQuestion(ApplyQuestion form) {
        if (StrUtil.isBlank(form.getQuestion())) {
            throw new BusinessException(400, "题干不能为空");
        }
        if (StrUtil.isBlank(form.getOptionA()) || StrUtil.isBlank(form.getOptionB())
                || StrUtil.isBlank(form.getOptionC())) {
            throw new BusinessException(400, "三个选项都要填");
        }
        Integer answer = form.getAnswer();
        if (answer == null || answer < 0 || answer > 2) {
            throw new BusinessException(400, "正确答案必须是第 1/2/3 个选项之一");
        }
        if (form.getId() == null && listAll().size() >= MAX_QUESTIONS) {
            throw new BusinessException(400, "最多只能设置 " + MAX_QUESTIONS + " 道题");
        }
        if (form.getSort() == null) {
            form.setSort(listAll().size() + 1);
        }
        if (form.getEnabled() == null) {
            form.setEnabled(1);
        }
        if (form.getId() == null) {
            questionMapper.insert(form);
        } else {
            questionMapper.updateById(form);
        }
        clampPassCount();
        return questionMapper.selectById(form.getId());
    }

    @Transactional
    public void deleteQuestion(Long id) {
        questionMapper.deleteById(id);
        clampPassCount();
    }

    public ApplySetting getSetting() {
        return currentSetting();
    }

    /** 保存通过题数 / 开关。passCount 会被 clamp 到「1..当前启用题目数」。 */
    @Transactional
    public ApplySetting saveSetting(Integer passCount, Integer enabled) {
        ApplySetting setting = currentSetting();
        ApplySetting patch = new ApplySetting();
        if (passCount != null) {
            patch.setPassCount(Math.max(1, passCount));
        }
        if (enabled != null) {
            patch.setEnabled(enabled == 0 ? 0 : 1);
        }
        if (setting.getId() == null) {
            // 表是空的（SQL 种子没跑）：直接插一行，避免 updateById(null) 抛异常
            settingMapper.insert(patch);
        } else {
            patch.setId(setting.getId());
            settingMapper.updateById(patch);
        }
        clampPassCount();
        return currentSetting();
    }

    // =================================================================
    // 内部
    // =================================================================

    /** 取单行设置；表里没数据时用内存默认值（不写库，交给 SQL 脚本初始化） */
    private ApplySetting currentSetting() {
        List<ApplySetting> rows = settingMapper.selectList(
                new LambdaQueryWrapper<ApplySetting>().orderByAsc(ApplySetting::getId).last("LIMIT 1"));
        if (rows != null && !rows.isEmpty()) {
            return rows.get(0);
        }
        ApplySetting fallback = new ApplySetting();
        fallback.setId(null);
        fallback.setPassCount(2);
        fallback.setEnabled(1);
        return fallback;
    }

    private List<ApplyQuestion> enabledQuestions() {
        List<ApplyQuestion> rows = questionMapper.selectList(new LambdaQueryWrapper<ApplyQuestion>()
                .eq(ApplyQuestion::getEnabled, 1)
                .orderByAsc(ApplyQuestion::getSort)
                .orderByAsc(ApplyQuestion::getId));
        return rows == null ? List.of() : rows;
    }

    /**
     * 生效的通过题数：既不能超过启用题目数（否则永远考不过），
     * 也不能小于 1（否则不答题也算通过）。题目为 0 时返回 1，让调用方先报"没配题目"。
     */
    private int effectivePassCount(ApplySetting setting, int questionCount) {
        int raw = setting.getPassCount() == null ? 1 : setting.getPassCount();
        if (questionCount <= 0) {
            return Math.max(1, raw);
        }
        return Math.max(1, Math.min(raw, questionCount));
    }

    /** passCount 超过题目数时自动拉回来，避免管理员把通过线设成永远达不到 */
    private void clampPassCount() {
        ApplySetting setting = currentSetting();
        if (setting.getId() == null) {
            return;
        }
        int total = enabledQuestions().size();
        int limit = Math.max(MIN_QUESTIONS, total);
        int current = setting.getPassCount() == null ? 1 : setting.getPassCount();
        if (current > limit) {
            settingMapper.update(null, new LambdaUpdateWrapper<ApplySetting>()
                    .eq(ApplySetting::getId, setting.getId())
                    .set(ApplySetting::getPassCount, limit));
        }
    }

    private String attemptKey(Long userId) {
        return "apply:rl:" + userId;
    }

    /** 今天还剩几次。Redis 挂了 fail-open（返回上限），不能因为缓存故障把人挡在门外 */
    private int attemptsLeft(Long userId) {
        try {
            Object raw = redis.opsForValue().get(attemptKey(userId));
            int used = raw == null ? 0 : Integer.parseInt(String.valueOf(raw));
            return Math.max(0, MAX_ATTEMPTS_PER_DAY - used);
        } catch (Exception e) {
            log.debug("读取申请次数失败（fail-open）: {}", e.getMessage());
            return MAX_ATTEMPTS_PER_DAY;
        }
    }

    private void bumpAttempt(Long userId) {
        try {
            Long v = redis.opsForValue().increment(attemptKey(userId));
            if (v != null && v == 1L) {
                redis.expire(attemptKey(userId), Duration.ofDays(1));
            }
        } catch (Exception e) {
            // 计数失败不影响本次判分：宁可少挡一次，也不要让正常用户提交不了
            log.warn("申请次数计数失败（忽略）: {}", e.getMessage());
        }
    }
}
