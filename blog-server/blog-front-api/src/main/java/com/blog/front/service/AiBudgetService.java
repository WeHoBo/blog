package com.blog.front.service;

import com.blog.common.entity.AiSetting;
import com.blog.common.entity.AiUsageDaily;
import com.blog.common.mapper.AiUsageDailyMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 全站日预算（P4）。
 *
 * <p>用途：个人限流挡的是"一个人问太多次"，挡不住"很多人各问一点"或"key 被盗刷"。
 * 后者唯一有效的护栏是钱 —— 今日全站花费达到 {@code ai_setting.global_daily_budget}
 * 就停止服务，次日 0 点自动恢复。
 *
 * <p>为什么读的是 {@code ai_usage_daily} 的 SUM 而不是 Redis 计数器：
 * 金额是 Java 侧按单价算出来的快照，写库时才算完；在 Redis 里另起一套累加等于
 * 两份账，一旦不一致就没人知道该信哪个。这里宁可慢一点，账只能有一份。
 *
 * <p>缓存 60 秒：SUM 走 {@code idx_date}，成本很低，但每个请求都查一次没必要。
 * 代价是**超支后最多持续 60 秒**才拦住 —— 按当前单次 ¥0.01~0.02 算，
 * 一分钟的漏拦是几毛钱级别，远小于把每次请求都变成一次聚合查询的代价。
 * 后台页面显示的是直查数据库的实时值，所以会出现"页面已红、用户还能再问 30 秒"的窗口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiBudgetService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 花费缓存时长（秒）。见类注释：这是"漏拦窗口"的上界 */
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private static final String COST_KEY_PREFIX = "ai:budget:cost:";

    private final AiUsageDailyMapper usageMapper;
    private final StringRedisTemplate redis;

    /** 预算额度存在 ai_setting 里，借它读（自带 5 秒缓存，后台改完 5 秒生效） */
    private final AiModelService modelService;

    /**
     * 预算状态
     *
     * @param spent     今日已花费（元）
     * @param budget    预算上限（元），0 表示不限
     * @param ratio     已用比例（budget 为 0 时返回 0）
     * @param exceeded  是否已超支（budget 为 0 时永远 false）
     * @param unlimited 是否未设预算
     */
    public record Status(BigDecimal spent, BigDecimal budget, double ratio,
                         boolean exceeded, boolean unlimited) {
    }

    public Status status() {
        BigDecimal budget = budgetOf();
        if (budget == null || budget.signum() <= 0) {
            return new Status(todayCost(), BigDecimal.ZERO, 0, false, true);
        }
        BigDecimal spent = todayCost();
        double ratio = spent.doubleValue() / budget.doubleValue();
        return new Status(spent, budget, ratio, spent.compareTo(budget) >= 0, false);
    }

    /** 今日全站花费。任何异常都返回 0（fail-open，见 {@link AiQuotaService} 的故障策略） */
    public BigDecimal todayCost() {
        String key = COST_KEY_PREFIX + LocalDate.now(ZONE).format(DAY_FMT);
        try {
            String hit = redis.opsForValue().get(key);
            if (hit != null) {
                return new BigDecimal(hit);
            }
        } catch (Exception e) {
            log.warn("读取预算缓存失败（改查数据库）: {}", e.getMessage());
        }

        BigDecimal cost = queryTodayCost();

        try {
            redis.opsForValue().set(key, cost.toPlainString(), CACHE_TTL);
        } catch (Exception e) {
            // 缓存写不进去不影响本次判断，只是下次还得查库
            log.debug("写预算缓存失败: {}", e.getMessage());
        }
        return cost;
    }

    private BigDecimal queryTodayCost() {
        try {
            QueryWrapper<AiUsageDaily> qw = new QueryWrapper<>();
            qw.select("COALESCE(SUM(cost), 0)").eq("stat_date", LocalDate.now(ZONE));
            List<Object> objs = usageMapper.selectObjs(qw);
            if (objs == null || objs.isEmpty() || objs.get(0) == null) {
                return BigDecimal.ZERO;
            }
            Object v = objs.get(0);
            return v instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            // 数据库读不到就当没花钱：与限流的 fail-open 保持一致 ——
            // 预算熔断是省钱手段，不能因为库抖一下就把全站 AI 停掉
            log.warn("查询今日 AI 花费失败，本次按 0 处理: {}", e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal budgetOf() {
        try {
            AiSetting s = modelService == null ? null : modelService.settingCached();
            return s == null ? null : s.getGlobalDailyBudget();
        } catch (Exception e) {
            log.warn("读取全站预算失败，按未设置处理: {}", e.getMessage());
            return null;
        }
    }
}
