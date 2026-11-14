package com.blog.front.service;

import com.blog.common.entity.User;
import com.blog.common.mapper.AiUsageDailyMapper;
import com.blog.common.mapper.UserMapper;
import com.blog.common.vo.AiTopUserVO;
import com.blog.common.vo.AiUsageByModelVO;
import com.blog.common.vo.AiUsageByUserVO;
import com.blog.common.vo.AiUsageSummaryVO;
import com.blog.common.vo.AiUsageTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用量报表（只读）。
 *
 * <p>与 {@link AiUsageService} 分开：那个负责"记账"，这个负责"看账"。
 * 混在一起会让写路径（每轮问答都要跑）被迫依赖报表逻辑和 UserMapper。
 *
 * <p>所有查询都打在 {@code ai_usage_daily} 上，不扫 {@code agent_trace} 明细 ——
 * 明细随使用量线性膨胀，图表会越用越慢。
 */
@Service
@RequiredArgsConstructor
public class AiUsageQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 前端最多看 90 天，再长没意义还会拖慢查询 */
    private static final int MAX_DAYS = 90;

    private final AiUsageDailyMapper usageMapper;
    private final UserMapper userMapper;

    /** 被拦记录只存在 Redis 里，借它读；本类不自己连 Redis */
    private final AiQuotaService quotaService;

    /** 今日各模型消耗，供模型列表的「今日消耗」列 */
    public Map<String, AiUsageByModelVO> byModelToday() {
        Map<String, AiUsageByModelVO> map = new HashMap<>();
        for (AiUsageByModelVO row : usageMapper.sumByModel(LocalDate.now(ZONE))) {
            if (row.getModelCode() != null) {
                map.put(row.getModelCode(), row);
            }
        }
        return map;
    }

    public Map<String, Object> summary(int days) {
        int d = clampDays(days);
        LocalDate today = LocalDate.now(ZONE);
        AiUsageSummaryVO vo = usageMapper.summary(today, today.minusDays(d - 1L));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", d);
        out.put("todayRequests", nz(vo == null ? null : vo.getTodayRequests()));
        out.put("todayTokens", nz(vo == null ? null : vo.getTodayTokens()));
        out.put("todayCost", money(vo == null ? null : vo.getTodayCost()));
        out.put("totalRequests", nz(vo == null ? null : vo.getTotalRequests()));
        out.put("totalTokens", nz(vo == null ? null : vo.getTotalTokens()));
        out.put("totalCost", money(vo == null ? null : vo.getTotalCost()));
        return out;
    }

    /**
     * 折线数据。
     *
     * <p>日期轴补齐到完整区间：缺的那天补 0，否则折线会在两个不相邻的日期之间直接连一条直线，
     * 看起来像"那几天有量"，实际是没人用。
     */
    public Map<String, Object> trend(int days, String groupBy) {
        int d = clampDays(days);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = today.minusDays(d - 1L);

        boolean byProvider = "provider".equalsIgnoreCase(groupBy);
        List<AiUsageTrendVO> rows = byProvider
                ? usageMapper.trendByProvider(from)
                : usageMapper.trendByModel(from);

        List<String> dates = new ArrayList<>(d);
        for (int i = 0; i < d; i++) {
            dates.add(from.plusDays(i).toString());
        }

        // groupKey -> (date -> 聚合值)
        Map<String, Map<String, BigDecimal[]>> bucket = new LinkedHashMap<>();
        for (AiUsageTrendVO r : rows) {
            if (r.getStatDate() == null) {
                continue;
            }
            String key = r.getGroupKey() == null || r.getGroupKey().isBlank() ? "未知" : r.getGroupKey();
            Map<String, BigDecimal[]> perDate = bucket.computeIfAbsent(key, k -> new HashMap<>());
            perDate.put(r.getStatDate().toString(), new BigDecimal[]{
                    BigDecimal.valueOf(nz(r.getRequestCount())),
                    BigDecimal.valueOf(nz(r.getTokens())),
                    r.getCost() == null ? BigDecimal.ZERO : r.getCost()
            });
        }

        List<Map<String, Object>> series = new ArrayList<>();
        // 金额大的排前面，图例顺序稳定，不会每次刷新乱跳
        List<Map.Entry<String, Map<String, BigDecimal[]>>> entries = new ArrayList<>(bucket.entrySet());
        entries.sort((a, b) -> totalCost(b.getValue()).compareTo(totalCost(a.getValue())));

        for (Map.Entry<String, Map<String, BigDecimal[]>> e : entries) {
            List<Integer> requests = new ArrayList<>(d);
            List<Long> tokens = new ArrayList<>(d);
            List<BigDecimal> cost = new ArrayList<>(d);
            for (String date : dates) {
                BigDecimal[] v = e.getValue().get(date);
                requests.add(v == null ? 0 : v[0].intValue());
                tokens.add(v == null ? 0L : v[1].longValue());
                cost.add(money(v == null ? BigDecimal.ZERO : v[2]));
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("key", e.getKey());
            s.put("requests", requests);
            s.put("tokens", tokens);
            s.put("cost", cost);
            series.add(s);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", d);
        out.put("groupBy", byProvider ? "provider" : "model");
        out.put("dates", dates);
        out.put("series", series);
        return out;
    }

    /**
     * 树状图数据（用户 × 模型）。
     *
     * <p>返回两层结构：外层每个方块是一个用户，点进去是该用户在各模型上的分解。
     * 「总消耗来自哪个用户」这个问题，面积最大的那一块就是答案 —— 不用读数字。
     */
    public Map<String, Object> byUser(int days) {
        int d = clampDays(days);
        LocalDate from = LocalDate.now(ZONE).minusDays(d - 1L);
        List<AiUsageByUserVO> rows = usageMapper.sumByUserAndModel(from);

        Map<Long, List<AiUsageByUserVO>> grouped = rows.stream()
                .collect(Collectors.groupingBy(AiUsageByUserVO::getUserId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, String> names = userNames(grouped.keySet());

        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> children = new ArrayList<>();
        for (Map.Entry<Long, List<AiUsageByUserVO>> e : grouped.entrySet()) {
            BigDecimal userCost = BigDecimal.ZERO;
            int userRequests = 0;
            List<Map<String, Object>> models = new ArrayList<>();
            for (AiUsageByUserVO r : e.getValue()) {
                BigDecimal c = r.getCost() == null ? BigDecimal.ZERO : r.getCost();
                userCost = userCost.add(c);
                userRequests += nz(r.getRequestCount());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", r.getModelCode() == null || r.getModelCode().isBlank() ? "未知模型" : r.getModelCode());
                m.put("cost", money(c));
                m.put("requests", nz(r.getRequestCount()));
                models.add(m);
            }
            models.sort((a, b) -> ((BigDecimal) b.get("cost")).compareTo((BigDecimal) a.get("cost")));

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", e.getKey());
            node.put("name", names.getOrDefault(e.getKey(), "用户#" + e.getKey()));
            node.put("cost", money(userCost));
            node.put("requests", userRequests);
            node.put("children", models);
            children.add(node);
            total = total.add(userCost);
        }
        children.sort((a, b) -> ((BigDecimal) b.get("cost")).compareTo((BigDecimal) a.get("cost")));

        BigDecimal finalTotal = total;
        for (Map<String, Object> node : children) {
            BigDecimal c = (BigDecimal) node.get("cost");
            node.put("ratio", finalTotal.signum() == 0 ? 0
                    : c.multiply(BigDecimal.valueOf(100)).divide(finalTotal, 1, RoundingMode.HALF_UP).doubleValue());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", d);
        out.put("total", money(total));
        out.put("children", children);
        return out;
    }

    public List<Map<String, Object>> topUsers(int days, int limit) {
        int d = clampDays(days);
        int n = Math.max(1, Math.min(100, limit));
        LocalDate from = LocalDate.now(ZONE).minusDays(d - 1L);
        List<AiTopUserVO> rows = usageMapper.topUsers(from, n);

        Map<Long, String> names = userNames(rows.stream().map(AiTopUserVO::getUserId).collect(Collectors.toList()));
        BigDecimal total = rows.stream()
                .map(r -> r.getCost() == null ? BigDecimal.ZERO : r.getCost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (AiTopUserVO r : rows) {
            BigDecimal c = r.getCost() == null ? BigDecimal.ZERO : r.getCost();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", r.getUserId());
            m.put("name", names.getOrDefault(r.getUserId(), "用户#" + r.getUserId()));
            m.put("requests", nz(r.getRequestCount()));
            m.put("tokens", nz(r.getTokens()));
            m.put("cost", money(c));
            m.put("ratio", total.signum() == 0 ? 0
                    : c.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue());
            m.put("lastActive", r.getLastActive() == null ? null : r.getLastActive().toString());
            out.add(m);
        }
        return out;
    }

    /**
     * 今日触发限流的用户。
     *
     * <p>没有这张表，管理员只会看到"用量突然变少"，但分不清是真没人用还是被拦了。
     * 数据源是 Redis 的计数（见 {@link AiQuotaService#recordBlocked}），不是数据库 ——
     * 被拦的请求压根没有落库的时机。
     */
    public List<Map<String, Object>> limitHits(int limit) {
        int n = Math.max(1, Math.min(100, limit));
        List<AiQuotaService.Hit> hits = quotaService == null ? List.of() : quotaService.todayHits(n);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = userNames(hits.stream().map(AiQuotaService.Hit::userId).toList());
        List<Map<String, Object>> out = new ArrayList<>(hits.size());
        for (AiQuotaService.Hit h : hits) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", h.userId());
            m.put("name", names.getOrDefault(h.userId(), "用户#" + h.userId()));
            m.put("count", h.count());
            m.put("scope", h.scope());
            m.put("scopeLabel", scopeLabel(h.scope()));
            m.put("lastAt", h.lastAt());
            out.add(m);
        }
        return out;
    }

    /** 触发层的展示名，前端不用再维护一份映射 */
    public static String scopeLabel(String scope) {
        if (scope == null) {
            return "";
        }
        return switch (scope) {
            case "minute" -> "每分钟";
            case "hourly" -> "每小时";
            case "daily" -> "每日";
            case "budget" -> "全站预算";
            case "ip" -> "IP 每日";
            case "concurrent" -> "并发";
            default -> scope;
        };
    }

    // ---------------- 内部 ----------------

    private Map<Long, String> userNames(java.util.Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        List<Long> clean = ids == null ? List.of()
                : ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return map;
        }
        for (User u : userMapper.selectBatchIds(clean)) {
            String name = u.getNickname() != null && !u.getNickname().isBlank()
                    ? u.getNickname() : u.getUsername();
            map.put(u.getId(), name == null ? "用户#" + u.getId() : name);
        }
        return map;
    }

    private int clampDays(int days) {
        if (days <= 0) {
            return 7;
        }
        return Math.min(days, MAX_DAYS);
    }

    private BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(4, RoundingMode.HALF_UP);
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private long nz(Long v) {
        return v == null ? 0L : v;
    }

    /** bucket 里每个日期是 [次数, token, 金额] 三元组，这里累加金额用于排序 */
    private BigDecimal totalCost(Map<String, BigDecimal[]> perDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal[] v : perDate.values()) {
            total = total.add(v[2] == null ? BigDecimal.ZERO : v[2]);
        }
        return total;
    }
}
