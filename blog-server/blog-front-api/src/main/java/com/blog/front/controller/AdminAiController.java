package com.blog.front.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.blog.common.dto.Result;
import com.blog.common.entity.AgentTrace;
import com.blog.common.entity.AiModel;
import com.blog.common.entity.AiSetting;
import com.blog.common.vo.AiUsageByModelVO;
import com.blog.front.service.AgentTraceService;
import com.blog.front.service.AiBudgetService;
import com.blog.front.service.AiModelService;
import com.blog.front.service.AiUsageQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 AI：模型注册表、全局设置、用量报表、轨迹。
 *
 * <p>权限挂在类上（{@code hasRole('admin')}），新增接口不会漏加注解 —— 默认继承管理员的读能力。
 *
 * <p><b>写操作和「花谁的钱」相关的，单独收紧到 {@code hasRole('super')}</b>：
 * 增删改模型、设默认、连通性测试、改预算/限额。原因很简单 ——
 * 这些接口一调就是「把全站 AI 切到另一个更贵的模型」，或者「把日预算改成 9999」，
 * 不应该和管理员写文章一个级别。方法级 {@code @PreAuthorize} 会覆盖类级注解，
 * 所以这里不需要把类注解也改成 super。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('admin')")
@RequiredArgsConstructor
public class AdminAiController {

    /** 连通性测试超时。厂商网关正常 1~3 秒，15 秒还没回就是真的不通 */
    private static final int TEST_TIMEOUT_MS = 15000;

    private final AiModelService modelService;
    private final AiUsageQueryService usageQueryService;
    private final AgentTraceService traceService;
    /** 预算熔断的闸门状态，只在这里读一次，不参与写 */
    private final AiBudgetService budgetService;

    // =================================================================
    // 模型
    // =================================================================

    @GetMapping("/models")
    public Result<List<Map<String, Object>>> listModels() {
        AiSetting setting = modelService.setting();
        Map<String, AiUsageByModelVO> today = usageQueryService.byModelToday();

        List<Map<String, Object>> out = new ArrayList<>();
        for (AiModel m : modelService.list()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("displayName", m.getDisplayName());
            row.put("provider", m.getProvider());
            row.put("modelId", m.getModelId());
            row.put("baseUrl", m.getBaseUrl());
            row.put("apiKeyMask", m.getApiKeyMask());
            row.put("apiKeyEnv", m.getApiKeyEnv());
            row.put("hasKey", StrUtil.isNotBlank(m.getApiKeyCipher()) || StrUtil.isNotBlank(m.getApiKeyEnv()));
            row.put("temperature", m.getTemperature());
            row.put("maxTokens", m.getMaxTokens());
            row.put("contextWindow", m.getContextWindow());
            row.put("supportStream", m.getSupportStream());
            row.put("supportTools", m.getSupportTools());
            row.put("inputPrice", m.getInputPrice());
            row.put("outputPrice", m.getOutputPrice());
            row.put("enabled", m.getEnabled());
            row.put("sort", m.getSort());
            row.put("remark", m.getRemark());
            row.put("isDefault", setting.getDefaultModelId() != null && setting.getDefaultModelId().equals(m.getId()));
            row.put("isFallback", setting.getFallbackModelId() != null && setting.getFallbackModelId().equals(m.getId()));

            AiUsageByModelVO u = today.get(m.getModelId());
            row.put("todayRequests", u == null ? 0 : (u.getRequestCount() == null ? 0 : u.getRequestCount()));
            row.put("todayCost", u == null || u.getCost() == null ? 0 : u.getCost());
            out.add(row);
        }
        return Result.ok(out);
    }

    @PostMapping("/models")
    @PreAuthorize("hasRole('super')")
    public Result<Map<String, Object>> createModel(@RequestBody AiModelService.ModelForm form) {
        AiModel m = modelService.create(form);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", m.getId());
        return Result.ok(data);
    }

    @PutMapping("/models/{id}")
    @PreAuthorize("hasRole('super')")
    public Result<?> updateModel(@PathVariable Long id, @RequestBody AiModelService.ModelForm form) {
        modelService.update(id, form);
        return Result.ok();
    }

    @DeleteMapping("/models/{id}")
    @PreAuthorize("hasRole('super')")
    public Result<?> deleteModel(@PathVariable Long id) {
        modelService.delete(id);
        return Result.ok();
    }

    @PostMapping("/models/{id}/default")
    @PreAuthorize("hasRole('super')")
    public Result<?> setDefault(@PathVariable Long id) {
        modelService.setDefault(id);
        return Result.ok();
    }

    /**
     * 连通性测试：发一条 max_tokens=5 的最小请求。
     *
     * <p>走服务端，前端永远接触不到 key。
     *
     * <p>env 模式下如果 blog-api 进程里没有那个环境变量（常见：key 只配在 RAG 的 .env 里），
     * 这里不做假失败，而是明确告诉管理员"去 RAG 侧验证" —— 否则会误判成密钥失效。
     */
    @PostMapping("/models/{id}/test")
    @PreAuthorize("hasRole('super')")
    public Result<Map<String, Object>> testModel(@PathVariable Long id) {
        AiModel m = modelService.require(id);
        String key = modelService.plainKeyOf(m);
        boolean fromEnv = false;
        if (StrUtil.isBlank(key) && StrUtil.isNotBlank(m.getApiKeyEnv())) {
            key = System.getenv(m.getApiKeyEnv());
            fromEnv = true;
        }
        if (StrUtil.isBlank(key)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("skipped", true);
            data.put("ok", false);
            data.put("message", "该模型的密钥配置在环境变量 " + StrUtil.blankToDefault(m.getApiKeyEnv(), "(未设置)")
                    + " 中，后台进程读不到。请在 RAG 服务所在环境验证，或改为在后台直接填写 API Key。");
            return Result.ok(data);
        }

        String url = StrUtil.removeSuffix(m.getBaseUrl(), "/") + "/chat/completions";
        JSONObject body = new JSONObject();
        body.put("model", m.getModelId());
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        body.put("max_tokens", 5);
        body.put("stream", false);

        long start = System.currentTimeMillis();
        HttpResponse resp = null;
        try {
            resp = HttpRequest.post(url)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .body(body.toJSONString())
                    .timeout(TEST_TIMEOUT_MS)
                    .execute();
            long ms = System.currentTimeMillis() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ms", ms);
            data.put("httpStatus", resp.getStatus());
            data.put("fromEnv", fromEnv);
            if (resp.getStatus() == 200) {
                data.put("ok", true);
                data.put("message", "✅ 连接正常，耗时 " + ms + "ms");
            } else {
                data.put("ok", false);
                data.put("message", "❌ HTTP " + resp.getStatus() + "：" + shortMessage(resp.body()));
            }
            return Result.ok(data);
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", false);
            data.put("ms", System.currentTimeMillis() - start);
            data.put("message", "❌ 请求失败：" + e.getClass().getSimpleName() + " " + e.getMessage());
            return Result.ok(data);
        } finally {
            if (resp != null) {
                resp.close();
            }
        }
    }

    // =================================================================
    // 全局设置
    // =================================================================

    @GetMapping("/setting")
    public Result<AiSetting> setting() {
        return Result.ok(modelService.setting());
    }

    @PutMapping("/setting")
    @PreAuthorize("hasRole('super')")
    public Result<AiSetting> updateSetting(@RequestBody AiModelService.SettingForm form) {
        Long operatorId = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            operatorId = id;
        }
        return Result.ok(modelService.updateSetting(form, operatorId));
    }

    // =================================================================
    // 用量报表
    // =================================================================

    @GetMapping("/usage/summary")
    public Result<Map<String, Object>> summary(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(usageQueryService.summary(days));
    }

    /**
     * 全站预算状态（今日已花 / 上限 / 是否已熔断）。
     *
     * <p>单独一个接口而不是塞进 summary：summary 是"历史报表"（可看 90 天），
     * 这个是"此刻的闸门状态"，前端要拿它决定是否在页头挂一条红字告警。
     * 注意它读的是熔断用的同一份 60 秒缓存，与 summary 的实时 todayCost 可能有细微差。
     */
    @GetMapping("/usage/budget")
    public Result<AiBudgetService.Status> budget() {
        return Result.ok(budgetService.status());
    }

    @GetMapping("/usage/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "14") int days,
                                             @RequestParam(defaultValue = "model") String groupBy) {
        return Result.ok(usageQueryService.trend(days, groupBy));
    }

    @GetMapping("/usage/by-user")
    public Result<Map<String, Object>> byUser(@RequestParam(defaultValue = "30") int days) {
        return Result.ok(usageQueryService.byUser(days));
    }

    @GetMapping("/usage/top-users")
    public Result<List<Map<String, Object>>> topUsers(@RequestParam(defaultValue = "30") int days,
                                                      @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(usageQueryService.topUsers(days, limit));
    }

    /** 今日触发限流的用户（数据在 Redis，只保留两天） */
    @GetMapping("/usage/limit-hits")
    public Result<List<Map<String, Object>>> limitHits(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(usageQueryService.limitHits(limit));
    }

    // =================================================================
    // 轨迹
    // =================================================================

    @GetMapping("/traces")
    public Result<Map<String, Object>> traces(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String model,
                                              @RequestParam(required = false) String status) {
        var p = traceService.page(page, size, userId, model, status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", p.getRecords().stream().map(this::traceRow).toList());
        data.put("total", p.getTotal());
        data.put("page", p.getCurrent());
        data.put("size", p.getSize());
        return Result.ok(data);
    }

    /** steps 是 JSON 字符串，直接回字符串让前端自行解析，避免在这里做一次无意义的反序列化 */
    private Map<String, Object> traceRow(AgentTrace t) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.getId());
        row.put("userId", t.getUserId());
        row.put("question", t.getQuestion());
        row.put("model", t.getModel());
        row.put("steps", t.getSteps());
        row.put("stepCount", t.getStepCount());
        row.put("promptTokens", t.getPromptTokens());
        row.put("completionTokens", t.getCompletionTokens());
        row.put("totalMs", t.getTotalMs());
        row.put("status", t.getStatus());
        row.put("errorMsg", t.getErrorMsg());
        row.put("createTime", t.getCreateTime() == null ? null : t.getCreateTime().toString());
        return row;
    }

    private String shortMessage(String body) {
        if (body == null) {
            return "";
        }
        try {
            JSONObject json = JSON.parseObject(body);
            Object err = json.get("error");
            if (err instanceof JSONObject e && e.get("message") != null) {
                return truncate(String.valueOf(e.get("message")));
            }
            if (json.get("message") != null) {
                return truncate(String.valueOf(json.get("message")));
            }
        } catch (Exception ignored) {
            // 不是 JSON 就原样截断
        }
        return truncate(body);
    }

    private String truncate(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "…";
    }
}
