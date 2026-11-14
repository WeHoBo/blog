package com.blog.front.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.entity.AiUsageDaily;
import com.blog.common.mapper.AiUsageDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 用量记账。
 *
 * 关键设计：cost 在**写入时**按当时单价算好并落库。
 * 若改成查询时联表乘单价，日后调整模型价格会把过去 30 天的历史账单一起改掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiUsageService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiUsageDailyMapper usageMapper;

    /** 模型注册表：单价以模型行上的为准，查不到才回落到下面的配置默认值 */
    private final AiModelService modelService;

    @Value("${ai.model-code:deepseek-chat}")
    private String defaultModelCode;

    /** 输入单价（元 / 百万 token），仅在模型注册表里查不到该模型时使用 */
    @Value("${ai.price.input:2.0}")
    private BigDecimal inputPrice;

    /** 输出单价（元 / 百万 token） */
    @Value("${ai.price.output:8.0}")
    private BigDecimal outputPrice;

    /**
     * 记一次用量。
     *
     * ⚠️ 失败请求也要记：模型已经生成了，钱已经花了，不记账就是自己骗自己。
     * 但记账失败不能影响用户（可能整个流程已经在收尾），所以异常一律吞掉只打日志。
     */
    public void record(Long userId, String modelCode, long promptTokens, long completionTokens) {
        try {
            recordInTransaction(userId, modelCode, promptTokens, completionTokens);
        } catch (Exception e) {
            log.warn("AI 用量记账失败（不影响用户）: user={} model={} pt={} ct={} err={}",
                    userId, modelCode, promptTokens, completionTokens, e.getMessage());
        }
    }

    /**
     * 与 agent_trace 明细写入同一个事务的版本：异常**必须抛出**让事务回滚。
     *
     * <p>分成两个方法是因为「记账失败要不要影响用户」和「要不要和明细同生共死」是两个问题：
     * 单独调用时要吞异常（不能让记账把用户已经拿到的回答搞没了），
     * 但一旦和 trace 同事务，就必须让异常冒出去，否则会出现「明细有、汇总没有」。
     */
    public void recordInTransaction(Long userId, String modelCode, long promptTokens, long completionTokens) {
        if (userId == null) {
            return;
        }
        String code = (modelCode == null || modelCode.isBlank()) ? defaultModelCode : modelCode.trim();
        long pt = Math.max(0, promptTokens);
        long ct = Math.max(0, completionTokens);

        // 单价优先取模型注册表（后台可改），查不到才回落配置文件 ——
        // 否则后台把某个贵模型的单价填好了，账单还是按全局默认价算，等于白填。
        AiModelService.Price price = modelService.priceByModelId(code);
        BigDecimal in = price == null ? inputPrice : price.input();
        BigDecimal out = price == null ? outputPrice : price.output();

        BigDecimal cost = in.multiply(BigDecimal.valueOf(pt))
                .add(out.multiply(BigDecimal.valueOf(ct)))
                .divide(MILLION, 6, RoundingMode.HALF_UP);
        usageMapper.upsertAdd(LocalDate.now(ZONE), userId, code, pt, ct, cost);
    }

    /** 今日汇总（跨模型合并），供 /api/ai/quota 返回 */
    public Map<String, Object> todaySummary(Long userId) {
        Map<String, Object> map = new HashMap<>();
        map.put("requestCount", 0);
        map.put("promptTokens", 0L);
        map.put("completionTokens", 0L);
        map.put("cost", BigDecimal.ZERO);
        if (userId == null) {
            return map;
        }
        try {
            List<AiUsageDaily> rows = usageMapper.selectList(new LambdaQueryWrapper<AiUsageDaily>()
                    .eq(AiUsageDaily::getStatDate, LocalDate.now(ZONE))
                    .eq(AiUsageDaily::getUserId, userId));
            int count = 0;
            long pt = 0;
            long ct = 0;
            BigDecimal cost = BigDecimal.ZERO;
            for (AiUsageDaily r : rows) {
                count += r.getRequestCount() == null ? 0 : r.getRequestCount();
                pt += r.getPromptTokens() == null ? 0 : r.getPromptTokens();
                ct += r.getCompletionTokens() == null ? 0 : r.getCompletionTokens();
                cost = cost.add(r.getCost() == null ? BigDecimal.ZERO : r.getCost());
            }
            map.put("requestCount", count);
            map.put("promptTokens", pt);
            map.put("completionTokens", ct);
            map.put("cost", cost.setScale(4, RoundingMode.HALF_UP));
        } catch (Exception e) {
            log.warn("查询 AI 今日用量失败: {}", e.getMessage());
        }
        return map;
    }
}
