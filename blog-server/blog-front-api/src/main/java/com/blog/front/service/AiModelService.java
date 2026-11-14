package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.entity.AiModel;
import com.blog.common.entity.AiSetting;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.AiModelMapper;
import com.blog.common.mapper.AiSettingMapper;
import com.blog.front.util.AiKeyCipher;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型注册表 + 全局设置。
 *
 * <p>多厂商不写 N 个适配器：国内主流厂商都提供 OpenAI 兼容端点，
 * 所以「换厂商」= 换 base_url + model_id + key，一行数据的事。
 *
 * <p><b>生效延迟 5 秒</b>：{@link #active()} 带 5 秒内存缓存，后台改完最迟 5 秒生效。
 * 任何写操作都会立即 {@link #invalidate()}，所以单实例是"立刻生效"，
 * 多实例最坏 5 秒 —— 这个数字是验收标准里的目标值。
 *
 * <p><b>密钥不在这层解析 env</b>：{@code apiKeyEnv} 模式下只把变量名传下去，
 * 由 RAG 读它自己的 .env。让 Java 去读环境变量会出问题 —— key 通常只配在 RAG 的 .env 里，
 * Java 进程里根本没有，硬读就会把线上密钥读成空字符串。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    /** 生效缓存时长。后台改完立即清缓存，这个值只影响多实例场景 */
    private static final long CACHE_MS = 5000;

    private static final Long SETTING_ID = 1L;

    private final AiModelMapper modelMapper;
    private final AiSettingMapper settingMapper;

    @Value("${ai.key-secret:}")
    private String keySecret;

    /** 当前生效配置的缓存 */
    private volatile ActiveModel cached;
    private volatile long cachedAt;

    /** 全局设置的缓存（限流阈值也从这里读，每轮请求都要用，不能每次查库） */
    private volatile AiSetting cachedSetting;
    private volatile long settingAt;

    // =================================================================
    // 对外：RAG 运行时配置
    // =================================================================

    /**
     * 给 RAG 用的运行配置。返回 null 表示注册表里没有可用模型，
     * 此时 RAG 会回落到自己的 .env（保证"后台配错也不至于全站 AI 挂掉"）。
     */
    public ActiveModel active() {
        long now = System.currentTimeMillis();
        ActiveModel hit = cached;
        if (hit != null && now - cachedAt < CACHE_MS) {
            return hit;
        }
        ActiveModel resolved = resolveActive();
        cached = resolved;
        cachedAt = now;
        return resolved;
    }

    /**
     * 降级模型：主模型返回 429/5xx/超时，RAG 会立刻用它重试一次（P4）。
     *
     * <p>返回 null 的情形一律当作"没配降级"：未设置、模型被删、被停用、与主模型是同一个。
     * 最后一种尤其要判——降级到自己等于没降级，只会让 RAG 白重试一次、把耗时翻倍。
     *
     * <p>不加缓存：它只在 {@code /internal/ai/model} 被调用时算一次，而那个接口本身
     * 有 5 秒缓存，频率极低，多一次主键查询没有意义。
     */
    public ActiveModel fallback() {
        try {
            AiSetting setting = settingCached();
            Long fallbackId = setting == null ? null : setting.getFallbackModelId();
            if (fallbackId == null) {
                return null;
            }
            AiModel m = modelMapper.selectById(fallbackId);
            if (m == null || !isEnabled(m)) {
                return null;
            }
            ActiveModel primary = active();
            if (primary != null && primary.id().equals(m.getId())) {
                return null;
            }
            return toActive(m);
        } catch (Exception e) {
            log.warn("读取降级模型失败（按未配置处理）: {}", e.getMessage());
            return null;
        }
    }

    private ActiveModel resolveActive() {
        try {
            AiSetting setting = settingCached();
            Long defaultId = setting == null ? null : setting.getDefaultModelId();

            AiModel model = null;
            if (defaultId != null) {
                model = modelMapper.selectById(defaultId);
            }
            if (model == null || !isEnabled(model)) {
                // 默认模型被删或停用：退回排序第一的启用模型，避免整站 AI 直接不可用
                List<AiModel> enabled = modelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                        .eq(AiModel::getEnabled, 1)
                        .orderByAsc(AiModel::getSort)
                        .orderByAsc(AiModel::getId)
                        .last("LIMIT 1"));
                model = enabled.isEmpty() ? null : enabled.get(0);
            }
            if (model == null) {
                return null;
            }
            return toActive(model);
        } catch (Exception e) {
            log.warn("读取生效模型失败，RAG 将回落到自身 .env 配置: {}", e.getMessage());
            return null;
        }
    }

    private ActiveModel toActive(AiModel m) {
        String plain = null;
        if (StrUtil.isNotBlank(m.getApiKeyCipher())) {
            plain = AiKeyCipher.decrypt(keySecret, m.getApiKeyCipher());
            if (plain == null) {
                // 解不开通常是主密钥换了：此时宁可不给 key（让 RAG 报错），也不要拿错 key 去请求
                log.warn("模型 {} 的密钥解密失败，请检查 ai.key-secret 是否被更换过；"
                        + "可在后台重新填写该模型的 API Key", m.getDisplayName());
            }
        }
        return new ActiveModel(
                m.getId(), m.getDisplayName(), m.getProvider(), m.getModelId(), m.getBaseUrl(),
                plain, m.getApiKeyEnv(),
                m.getTemperature() == null ? new BigDecimal("0.30") : m.getTemperature(),
                m.getMaxTokens() == null ? 4096 : m.getMaxTokens(),
                m.getContextWindow() == null ? 64000 : m.getContextWindow(),
                m.getSupportTools() != null && m.getSupportTools() == 1,
                m.getInputPrice() == null ? BigDecimal.ZERO : m.getInputPrice(),
                m.getOutputPrice() == null ? BigDecimal.ZERO : m.getOutputPrice());
    }

    /**
     * 按 model_id 反查单价，供记账使用。
     *
     * <p>查不到返回 null，调用方回落到 application.yml 里的 ai.price.*。
     * 这里刻意不加缓存：一次 AI 请求要跑好几秒，多一条走索引的查询无所谓，
     * 反而能保证"后台改完单价，下一次记账就用新价"。
     */
    public Price priceByModelId(String modelId) {
        if (StrUtil.isBlank(modelId)) {
            return null;
        }
        try {
            AiModel m = modelMapper.selectOne(new LambdaQueryWrapper<AiModel>()
                    .eq(AiModel::getModelId, modelId.trim())
                    .last("LIMIT 1"));
            if (m == null) {
                return null;
            }
            return new Price(
                    m.getInputPrice() == null ? BigDecimal.ZERO : m.getInputPrice(),
                    m.getOutputPrice() == null ? BigDecimal.ZERO : m.getOutputPrice());
        } catch (Exception e) {
            return null;
        }
    }

    public void invalidate() {
        cached = null;
        cachedAt = 0;
        cachedSetting = null;
        settingAt = 0;
    }

    /**
     * 带 5 秒缓存的全局设置。
     *
     * <p>限流判定每轮请求都要用（分钟/小时/天三个阈值），直接查库等于每次 AI 调用多三条 SQL，
     * 所以这里必须缓存。后台改完立即 invalidate，单实例立刻生效。
     */
    public AiSetting settingCached() {
        long now = System.currentTimeMillis();
        AiSetting hit = cachedSetting;
        if (hit != null && now - settingAt < CACHE_MS) {
            return hit;
        }
        AiSetting fresh = setting();
        cachedSetting = fresh;
        settingAt = now;
        return fresh;
    }

    // =================================================================
    // 对外：后台管理
    // =================================================================

    public List<AiModel> list() {
        return modelMapper.selectList(new LambdaQueryWrapper<AiModel>()
                .orderByAsc(AiModel::getSort)
                .orderByAsc(AiModel::getId));
    }

    public AiModel require(Long id) {
        AiModel m = id == null ? null : modelMapper.selectById(id);
        if (m == null) {
            throw new BusinessException(404, "模型不存在");
        }
        return m;
    }

    public AiSetting setting() {
        AiSetting s = settingMapper.selectById(SETTING_ID);
        if (s == null) {
            // 表被手工清过也要能跑：缺行时用默认值补一条，而不是让整个 AI 不可用
            s = new AiSetting();
            s.setId(SETTING_ID);
            s.setAllowUserChoice(0);
            s.setMinuteLimit(5);
            s.setHourlyLimit(30);
            s.setDailyLimit(100);
            s.setDailyTokenLimit(0);
            s.setGlobalDailyBudget(BigDecimal.ZERO);
            s.setConfigVersion(1L);
            s.setCreateTime(LocalDateTime.now());
            s.setUpdateTime(LocalDateTime.now());
            settingMapper.insert(s);
        }
        return s;
    }

    @Transactional
    public AiModel create(ModelForm form) {
        validate(form, true);
        AiModel m = new AiModel();
        apply(m, form, true);
        m.setIsDeleted(0);
        modelMapper.insert(m);
        invalidate();
        return m;
    }

    @Transactional
    public AiModel update(Long id, ModelForm form) {
        AiModel m = require(id);
        validate(form, false);
        apply(m, form, false);
        modelMapper.updateById(m);
        invalidate();
        return m;
    }

    /**
     * 删除。
     *
     * <p>默认模型不允许删 —— 否则 active() 会静默退回"排序第一"，管理员以为自己删对了，
     * 实际上换了个模型在跑。必须先切默认，再删。
     */
    @Transactional
    public void delete(Long id) {
        AiModel m = require(id);
        AiSetting s = setting();
        if (s.getDefaultModelId() != null && s.getDefaultModelId().equals(id)) {
            throw new BusinessException(400, "这是当前默认模型，请先把默认模型切到别的模型再删除");
        }
        if (s.getFallbackModelId() != null && s.getFallbackModelId().equals(id)) {
            throw new BusinessException(400, "该模型被设为降级模型，请先解除再删除");
        }
        modelMapper.deleteById(id);   // 逻辑删除：历史用量还要能按 model_code 关联
        invalidate();
    }

    @Transactional
    public void setDefault(Long id) {
        AiModel m = require(id);
        if (!isEnabled(m)) {
            throw new BusinessException(400, "停用的模型不能设为默认");
        }
        AiSetting s = setting();
        s.setDefaultModelId(id);
        s.setConfigVersion((s.getConfigVersion() == null ? 1 : s.getConfigVersion()) + 1);
        s.setUpdateTime(LocalDateTime.now());
        settingMapper.updateById(s);
        invalidate();
    }

    @Transactional
    public AiSetting updateSetting(SettingForm form, Long operatorId) {
        AiSetting s = setting();
        if (form.getDefaultModelId() != null) {
            AiModel m = require(form.getDefaultModelId());
            if (!isEnabled(m)) {
                throw new BusinessException(400, "停用的模型不能设为默认");
            }
            s.setDefaultModelId(form.getDefaultModelId());
        }
        if (form.getFallbackModelId() != null) {
            if (form.getFallbackModelId() == 0) {
                s.setFallbackModelId(null);
            } else {
                require(form.getFallbackModelId());
                s.setFallbackModelId(form.getFallbackModelId());
            }
        }
        if (form.getAllowUserChoice() != null) {
            s.setAllowUserChoice(form.getAllowUserChoice());
        }
        if (form.getMinuteLimit() != null) {
            s.setMinuteLimit(Math.max(0, form.getMinuteLimit()));
        }
        if (form.getHourlyLimit() != null) {
            s.setHourlyLimit(Math.max(0, form.getHourlyLimit()));
        }
        if (form.getDailyLimit() != null) {
            s.setDailyLimit(Math.max(0, form.getDailyLimit()));
        }
        if (form.getDailyTokenLimit() != null) {
            s.setDailyTokenLimit(Math.max(0, form.getDailyTokenLimit()));
        }
        if (form.getGlobalDailyBudget() != null) {
            s.setGlobalDailyBudget(form.getGlobalDailyBudget().max(BigDecimal.ZERO));
        }
        s.setConfigVersion((s.getConfigVersion() == null ? 1 : s.getConfigVersion()) + 1);
        s.setUpdatedBy(operatorId);
        s.setUpdateTime(LocalDateTime.now());
        settingMapper.updateById(s);
        invalidate();
        return s;
    }

    /** 连通性测试用：把解密后的 key 交给调用方，前端永远拿不到 */
    public String plainKeyOf(AiModel m) {
        if (StrUtil.isNotBlank(m.getApiKeyCipher())) {
            return AiKeyCipher.decrypt(keySecret, m.getApiKeyCipher());
        }
        return null;
    }

    // =================================================================
    // 内部
    // =================================================================

    private boolean isEnabled(AiModel m) {
        return m.getEnabled() != null && m.getEnabled() == 1;
    }

    private void validate(ModelForm f, boolean isCreate) {
        if (isCreate || f.getDisplayName() != null) {
            if (StrUtil.isBlank(f.getDisplayName())) {
                throw new BusinessException(400, "显示名不能为空");
            }
        }
        if (isCreate) {
            if (StrUtil.isBlank(f.getProvider())) {
                throw new BusinessException(400, "厂商不能为空");
            }
            if (StrUtil.isBlank(f.getModelId())) {
                throw new BusinessException(400, "模型 ID 不能为空");
            }
            if (StrUtil.isBlank(f.getBaseUrl())) {
                throw new BusinessException(400, "base_url 不能为空");
            }
        }
        if (f.getBaseUrl() != null && StrUtil.isNotBlank(f.getBaseUrl())
                && !f.getBaseUrl().trim().startsWith("http")) {
            throw new BusinessException(400, "base_url 必须以 http:// 或 https:// 开头");
        }
        if (f.getTemperature() != null
                && (f.getTemperature().compareTo(BigDecimal.ZERO) < 0
                || f.getTemperature().compareTo(new BigDecimal("2")) > 0)) {
            throw new BusinessException(400, "temperature 必须在 0 ~ 2 之间");
        }
        if (StrUtil.isNotBlank(f.getApiKey()) && !AiKeyCipher.available(keySecret)) {
            throw new BusinessException(400,
                    "服务端未配置 ai.key-secret，无法加密保存密钥。请在服务器设置该环境变量后重启，"
                            + "或改用「密钥来自环境变量」模式（apiKeyEnv）");
        }
        if (isCreate && StrUtil.isNotBlank(f.getApiKey())) {
            // 新增时同时给了 cipher 模式与 env 模式，容易让人误判到底用的哪个
            if (StrUtil.isNotBlank(f.getApiKeyEnv())) {
                throw new BusinessException(400, "API Key 与环境变量名只能留一个");
            }
        }
    }

    private void apply(AiModel m, ModelForm f, boolean isCreate) {
        if (f.getDisplayName() != null) {
            m.setDisplayName(f.getDisplayName().trim());
        }
        if (f.getProvider() != null) {
            m.setProvider(f.getProvider().trim());
        }
        if (f.getModelId() != null) {
            m.setModelId(f.getModelId().trim());
        }
        if (f.getBaseUrl() != null) {
            // 统一去掉尾部斜杠：拼 /chat/completions 时少一层双斜杠，部分厂商会 404
            m.setBaseUrl(StrUtil.removeSuffix(f.getBaseUrl().trim(), "/"));
        }
        if (f.getApiKeyEnv() != null) {
            m.setApiKeyEnv(StrUtil.blankToDefault(f.getApiKeyEnv().trim(), null));
        }
        // apiKey 留空 = 不修改（后台编辑表单不回显明文，所以不能用"空就是清空"的语义）
        if (StrUtil.isNotBlank(f.getApiKey())) {
            m.setApiKeyCipher(AiKeyCipher.encrypt(keySecret, f.getApiKey().trim()));
            m.setApiKeyMask(AiKeyCipher.mask(f.getApiKey().trim()));
            m.setApiKeyEnv(null);   // 两种模式互斥
        } else if (Boolean.TRUE.equals(f.getClearApiKey())) {
            m.setApiKeyCipher(null);
            m.setApiKeyMask(null);
        }
        if (f.getTemperature() != null) {
            m.setTemperature(f.getTemperature());
        }
        if (f.getMaxTokens() != null) {
            m.setMaxTokens(Math.max(1, f.getMaxTokens()));
        }
        if (f.getContextWindow() != null) {
            m.setContextWindow(Math.max(1000, f.getContextWindow()));
        }
        if (f.getSupportStream() != null) {
            m.setSupportStream(f.getSupportStream());
        }
        if (f.getSupportTools() != null) {
            m.setSupportTools(f.getSupportTools());
        }
        if (f.getInputPrice() != null) {
            m.setInputPrice(f.getInputPrice().max(BigDecimal.ZERO));
        }
        if (f.getOutputPrice() != null) {
            m.setOutputPrice(f.getOutputPrice().max(BigDecimal.ZERO));
        }
        if (f.getEnabled() != null) {
            m.setEnabled(f.getEnabled());
        }
        if (f.getSort() != null) {
            m.setSort(f.getSort());
        }
        if (f.getRemark() != null) {
            m.setRemark(f.getRemark());
        }
        if (isCreate) {
            if (m.getTemperature() == null) {
                m.setTemperature(new BigDecimal("0.30"));
            }
            if (m.getMaxTokens() == null) {
                m.setMaxTokens(4096);
            }
            if (m.getContextWindow() == null) {
                m.setContextWindow(64000);
            }
            if (m.getSupportStream() == null) {
                m.setSupportStream(1);
            }
            if (m.getSupportTools() == null) {
                m.setSupportTools(0);
            }
            if (m.getInputPrice() == null) {
                m.setInputPrice(BigDecimal.ZERO);
            }
            if (m.getOutputPrice() == null) {
                m.setOutputPrice(BigDecimal.ZERO);
            }
            if (m.getEnabled() == null) {
                m.setEnabled(1);
            }
            if (m.getSort() == null) {
                m.setSort(0);
            }
        }
    }

    // =================================================================
    // 数据结构
    // =================================================================

    /**
     * 传给 RAG 的运行配置。
     *
     * @param apiKey    后台填的密钥（已解密），env 模式时为 null
     * @param apiKeyEnv 密钥所在环境变量名，由 RAG 自己去读自己的 .env
     */
    public record ActiveModel(
            Long id, String displayName, String provider, String modelId, String baseUrl,
            String apiKey, String apiKeyEnv,
            BigDecimal temperature, Integer maxTokens, Integer contextWindow,
            boolean supportTools, BigDecimal inputPrice, BigDecimal outputPrice) {
    }

    public record Price(BigDecimal input, BigDecimal output) {
    }

    @Data
    public static class ModelForm {
        private String displayName;
        private String provider;
        private String modelId;
        private String baseUrl;
        /** 留空表示不修改已有密钥 */
        private String apiKey;
        /** 密钥改由环境变量提供时填变量名 */
        private String apiKeyEnv;
        /** 显式清空密钥（与 apiKey 留空语义不同） */
        private Boolean clearApiKey;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Integer contextWindow;
        private Integer supportStream;
        private Integer supportTools;
        private BigDecimal inputPrice;
        private BigDecimal outputPrice;
        private Integer enabled;
        private Integer sort;
        private String remark;
    }

    @Data
    public static class SettingForm {
        private Long defaultModelId;
        /** 传 0 表示清除降级模型 */
        private Long fallbackModelId;
        private Integer allowUserChoice;
        private Integer minuteLimit;
        private Integer hourlyLimit;
        private Integer dailyLimit;
        private Integer dailyTokenLimit;
        private BigDecimal globalDailyBudget;
    }
}
