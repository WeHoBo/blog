package com.blog.front.service;

import com.blog.common.entity.AiModel;
import com.blog.common.entity.AiSetting;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.AiModelMapper;
import com.blog.common.mapper.AiSettingMapper;
import com.blog.front.util.AiKeyCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型注册表的核心契约。
 *
 * 这里锁的是三件"错了不会报错只会悄悄错"的事：
 *   1. 默认模型切了要真的生效（不然后台点了没反应）
 *   2. 5 秒缓存不能把"后台改了"永远挡住
 *   3. 密钥留空保存**不能**把已有密钥清掉（最常见的手滑）
 */
class AiModelServiceTest {

    private static final String SECRET = "unit-test-master-secret";

    private AiModelMapper modelMapper;
    private AiSettingMapper settingMapper;
    private AiModelService service;

    @BeforeEach
    void setUp() {
        modelMapper = mock(AiModelMapper.class);
        settingMapper = mock(AiSettingMapper.class);
        service = new AiModelService(modelMapper, settingMapper);
        ReflectionTestUtils.setField(service, "keySecret", SECRET);
    }

    // ---------------- 生效配置 ----------------

    /** 默认模型应当被选中，且 base_url 原样传给 RAG */
    @Test
    void active_returnsDefaultModel() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "deepseek-chat", 1));

        AiModelService.ActiveModel active = service.active();

        assertNotNull(active);
        assertEquals("deepseek-chat", active.modelId());
        assertEquals("https://api.deepseek.com", active.baseUrl());
        assertTrue(active.supportTools());
    }

    /** 默认模型被停用 → 退回排序第一的启用模型，不能让整站 AI 直接不可用 */
    @Test
    void active_fallsBackWhenDefaultDisabled() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "disabled-model", 0));

        AiModel m = model(11L, "backup-model", 1);
        when(modelMapper.selectList(any())).thenReturn(List.of(m));

        AiModelService.ActiveModel active = service.active();

        assertNotNull(active);
        assertEquals("backup-model", active.modelId());
    }

    /** 一个启用模型都没有 → 返回 null，RAG 回落到自己的 .env */
    @Test
    void active_returnsNullWhenNoEnabledModel() {
        when(settingMapper.selectById(1L)).thenReturn(setting(null));
        when(modelMapper.selectList(any())).thenReturn(List.of());

        assertNull(service.active());
    }

    /** 5 秒缓存内不重复查库（否则每轮问答多两条 SQL） */
    @Test
    void active_usesCacheWithinWindow() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "m", 1));

        service.active();
        service.active();
        service.active();

        verify(modelMapper, times(1)).selectById(10L);
    }

    /** 后台改完必须能立刻生效（invalidate 清缓存） */
    @Test
    void invalidate_forcesReload() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "old-model", 1));
        service.active();

        service.invalidate();
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "new-model", 1));

        assertEquals("new-model", service.active().modelId());
        verify(modelMapper, times(2)).selectById(10L);
    }

    /** 后台填的密钥要解密后交给 RAG */
    @Test
    void active_decryptsCipherKey() {
        AiModel m = model(10L, "m", 1);
        m.setApiKeyCipher(AiKeyCipher.encrypt(SECRET, "sk-db-key"));
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(m);

        AiModelService.ActiveModel active = service.active();

        assertEquals("sk-db-key", active.apiKey());
        assertNull(active.apiKeyEnv());
    }

    /** env 模式只传变量名，**不传值** —— key 通常只配在 RAG 的 .env 里，Java 进程里没有 */
    @Test
    void active_envMode_passesNameNotValue() {
        AiModel m = model(10L, "m", 1);
        m.setApiKeyEnv("DEEPSEEK_API_KEY");
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(m);

        AiModelService.ActiveModel active = service.active();

        assertNull(active.apiKey());
        assertEquals("DEEPSEEK_API_KEY", active.apiKeyEnv());
    }

    // ---------------- 单价 ----------------

    @Test
    void priceByModelId_readsModelRow() {
        when(modelMapper.selectOne(any())).thenReturn(model(10L, "m", 1));

        AiModelService.Price p = service.priceByModelId("m");

        assertNotNull(p);
        assertEquals(new BigDecimal("1.0000"), p.input());
        assertEquals(new BigDecimal("2.0000"), p.output());
    }

    @Test
    void priceByModelId_unknownReturnsNull() {
        when(modelMapper.selectOne(any())).thenReturn(null);
        assertNull(service.priceByModelId("nope"));
        assertNull(service.priceByModelId("  "));
    }

    // ---------------- 写操作校验 ----------------

    /** 默认模型不允许删：否则 active() 会静默换成别的模型，管理员以为删对了 */
    @Test
    void delete_refusesDefaultModel() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(10L)).thenReturn(model(10L, "m", 1));

        BusinessException e = assertThrows(BusinessException.class, () -> service.delete(10L));
        assertTrue(e.getMessage().contains("默认模型"));
        verify(modelMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_softDeletesNonDefault() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(11L)).thenReturn(model(11L, "other", 1));

        service.delete(11L);

        verify(modelMapper).deleteById(11L);
    }

    @Test
    void create_rejectsNonHttpBaseUrl() {
        AiModelService.ModelForm f = form();
        f.setBaseUrl("api.deepseek.com");

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(f));
        assertTrue(e.getMessage().contains("http"));
    }

    /** 没配主密钥时必须拒绝保存密钥，而不是悄悄存明文 */
    @Test
    void create_rejectsApiKeyWhenNoSecret() {
        ReflectionTestUtils.setField(service, "keySecret", "");
        AiModelService.ModelForm f = form();
        f.setApiKey("sk-xxx");

        BusinessException e = assertThrows(BusinessException.class, () -> service.create(f));
        assertTrue(e.getMessage().contains("ai.key-secret"));
    }

    /** cipher 模式与 env 模式不能同时给，否则说不清到底用哪个 */
    @Test
    void create_rejectsBothKeyModes() {
        AiModelService.ModelForm f = form();
        f.setApiKey("sk-xxx");
        f.setApiKeyEnv("DEEPSEEK_API_KEY");

        assertThrows(BusinessException.class, () -> service.create(f));
    }

    /** ⭐ 编辑时 apiKey 留空 = 不修改。这条错了会把线上密钥清成空，AI 直接全挂 */
    @Test
    void update_blankApiKeyKeepsExisting() {
        AiModel existing = model(10L, "m", 1);
        existing.setApiKeyCipher(AiKeyCipher.encrypt(SECRET, "sk-existing"));
        existing.setApiKeyMask("sk-****ting");
        when(modelMapper.selectById(10L)).thenReturn(existing);

        AiModelService.ModelForm f = new AiModelService.ModelForm();
        f.setDisplayName("新名字");
        // apiKey 不设置（null）

        service.update(10L, f);

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).updateById(captor.capture());
        assertEquals("sk-existing", AiKeyCipher.decrypt(SECRET, captor.getValue().getApiKeyCipher()));
        assertEquals("新名字", captor.getValue().getDisplayName());
    }

    /** 显式 clearApiKey 才清空 */
    @Test
    void update_clearApiKeyWipesKey() {
        AiModel existing = model(10L, "m", 1);
        existing.setApiKeyCipher(AiKeyCipher.encrypt(SECRET, "sk-existing"));
        when(modelMapper.selectById(10L)).thenReturn(existing);

        AiModelService.ModelForm f = new AiModelService.ModelForm();
        f.setClearApiKey(true);

        service.update(10L, f);

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).updateById(captor.capture());
        assertNull(captor.getValue().getApiKeyCipher());
        assertNull(captor.getValue().getApiKeyMask());
    }

    /** 填了新密钥则写入密文 + 掩码，并清掉 env 模式（互斥） */
    @Test
    void update_newApiKeyEncryptsAndDropsEnvMode() {
        AiModel existing = model(10L, "m", 1);
        existing.setApiKeyEnv("OLD_ENV");
        when(modelMapper.selectById(10L)).thenReturn(existing);

        AiModelService.ModelForm f = new AiModelService.ModelForm();
        f.setApiKey("sk-new-key-123456");

        service.update(10L, f);

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).updateById(captor.capture());
        assertEquals("sk-new-key-123456", AiKeyCipher.decrypt(SECRET, captor.getValue().getApiKeyCipher()));
        assertNotNull(captor.getValue().getApiKeyMask());
        assertNull(captor.getValue().getApiKeyEnv());
    }

    /** base_url 尾部斜杠要去掉，否则拼 /chat/completions 时会出现双斜杠，部分厂商 404 */
    @Test
    void create_trimsTrailingSlash() {
        AiModelService.ModelForm f = form();
        f.setBaseUrl("https://api.deepseek.com/");

        service.create(f);

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).insert(captor.capture());
        assertEquals("https://api.deepseek.com", captor.getValue().getBaseUrl());
    }

    /** temperature 越界要拒 */
    @Test
    void create_rejectsTemperatureOutOfRange() {
        AiModelService.ModelForm f = form();
        f.setTemperature(new BigDecimal("3"));

        assertThrows(BusinessException.class, () -> service.create(f));
    }

    /** 设为默认时会把 config_version +1（供运行时缓存失效） */
    @Test
    void setDefault_bumpsConfigVersion() {
        AiSetting s = setting(10L);
        s.setConfigVersion(7L);
        when(settingMapper.selectById(1L)).thenReturn(s);
        when(modelMapper.selectById(11L)).thenReturn(model(11L, "m2", 1));

        service.setDefault(11L);

        ArgumentCaptor<AiSetting> captor = ArgumentCaptor.forClass(AiSetting.class);
        verify(settingMapper).updateById(captor.capture());
        assertEquals(11L, captor.getValue().getDefaultModelId().longValue());
        assertEquals(8L, captor.getValue().getConfigVersion().longValue());
    }

    /** 停用的模型不能设为默认 */
    @Test
    void setDefault_rejectsDisabledModel() {
        when(settingMapper.selectById(1L)).thenReturn(setting(10L));
        when(modelMapper.selectById(11L)).thenReturn(model(11L, "off", 0));

        assertThrows(BusinessException.class, () -> service.setDefault(11L));
    }

    // ---------------- 构造辅助 ----------------

    private AiSetting setting(Long defaultId) {
        AiSetting s = new AiSetting();
        s.setId(1L);
        s.setDefaultModelId(defaultId);
        s.setMinuteLimit(5);
        s.setHourlyLimit(30);
        s.setDailyLimit(100);
        s.setConfigVersion(1L);
        return s;
    }

    private AiModel model(Long id, String modelId, int enabled) {
        AiModel m = new AiModel();
        m.setId(id);
        m.setDisplayName("模型" + id);
        m.setProvider("deepseek");
        m.setModelId(modelId);
        m.setBaseUrl("https://api.deepseek.com");
        m.setTemperature(new BigDecimal("0.30"));
        m.setMaxTokens(4096);
        m.setContextWindow(64000);
        m.setSupportStream(1);
        m.setSupportTools(1);
        m.setInputPrice(new BigDecimal("1.0000"));
        m.setOutputPrice(new BigDecimal("2.0000"));
        m.setEnabled(enabled);
        m.setSort(0);
        m.setIsDeleted(0);
        return m;
    }

    private AiModelService.ModelForm form() {
        AiModelService.ModelForm f = new AiModelService.ModelForm();
        f.setDisplayName("新模型");
        f.setProvider("deepseek");
        f.setModelId("deepseek-chat");
        f.setBaseUrl("https://api.deepseek.com");
        return f;
    }
}
