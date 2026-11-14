package com.blog.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.common.entity.AiUsageDaily;
import com.blog.common.vo.AiTopUserVO;
import com.blog.common.vo.AiUsageByModelVO;
import com.blog.common.vo.AiUsageByUserVO;
import com.blog.common.vo.AiUsageSummaryVO;
import com.blog.common.vo.AiUsageTrendVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AiUsageDailyMapper extends BaseMapper<AiUsageDaily> {

    /**
     * 原子累加（INSERT ... ON DUPLICATE KEY UPDATE）。
     *
     * ⚠️ 必须用一条 SQL 完成，不能"先 select 再 update"：
     * 两个并发请求会各自读到旧值再写回，后写的覆盖先写的，用量凭空变少。
     */
    @Insert("INSERT INTO ai_usage_daily"
            + " (stat_date, user_id, model_code, request_count, prompt_tokens, completion_tokens, cost)"
            + " VALUES (#{statDate}, #{userId}, #{modelCode}, 1, #{promptTokens}, #{completionTokens}, #{cost})"
            + " ON DUPLICATE KEY UPDATE"
            + " request_count = request_count + 1,"
            + " prompt_tokens = prompt_tokens + #{promptTokens},"
            + " completion_tokens = completion_tokens + #{completionTokens},"
            + " cost = cost + #{cost}")
    int upsertAdd(@Param("statDate") LocalDate statDate,
                  @Param("userId") Long userId,
                  @Param("modelCode") String modelCode,
                  @Param("promptTokens") long promptTokens,
                  @Param("completionTokens") long completionTokens,
                  @Param("cost") BigDecimal cost);

    // =================================================================
    // 管理端报表查询
    // 全部走 idx_date / idx_user 索引，不做全表扫。
    // 聚合只用 SUM，不查 agent_trace 明细 —— 明细会随时间线性膨胀，图表会被拖死。
    // =================================================================

    /** 指定日期的各模型用量（模型列表的「今日消耗」列） */
    @Select("SELECT model_code AS modelCode,"
            + " SUM(request_count) AS requestCount,"
            + " SUM(prompt_tokens + completion_tokens) AS tokens,"
            + " SUM(cost) AS cost"
            + " FROM ai_usage_daily WHERE stat_date = #{date}"
            + " GROUP BY model_code")
    List<AiUsageByModelVO> sumByModel(@Param("date") LocalDate date);

    /** 按「日期 × 模型」的每日序列 */
    @Select("SELECT stat_date AS statDate, model_code AS groupKey,"
            + " SUM(request_count) AS requestCount,"
            + " SUM(prompt_tokens + completion_tokens) AS tokens,"
            + " SUM(cost) AS cost"
            + " FROM ai_usage_daily WHERE stat_date >= #{from}"
            + " GROUP BY stat_date, model_code ORDER BY stat_date")
    List<AiUsageTrendVO> trendByModel(@Param("from") LocalDate from);

    /**
     * 按「日期 × 厂商」的每日序列。
     *
     * <p>用 LEFT JOIN 而不是内连接：模型被删掉（is_deleted=1）后历史用量还要能统计，
     * 关联不上就退回 model_code 本身，不至于让那部分钱凭空消失。
     */
    @Select("SELECT u.stat_date AS statDate,"
            + " COALESCE(m.provider, u.model_code) AS groupKey,"
            + " SUM(u.request_count) AS requestCount,"
            + " SUM(u.prompt_tokens + u.completion_tokens) AS tokens,"
            + " SUM(u.cost) AS cost"
            + " FROM ai_usage_daily u"
            + " LEFT JOIN ai_model m ON m.model_id = u.model_code"
            + " WHERE u.stat_date >= #{from}"
            + " GROUP BY u.stat_date, COALESCE(m.provider, u.model_code)"
            + " ORDER BY u.stat_date")
    List<AiUsageTrendVO> trendByProvider(@Param("from") LocalDate from);

    /** 树状图：用户 × 模型的消耗分解 */
    @Select("SELECT user_id AS userId, model_code AS modelCode,"
            + " SUM(request_count) AS requestCount, SUM(cost) AS cost"
            + " FROM ai_usage_daily WHERE stat_date >= #{from}"
            + " GROUP BY user_id, model_code")
    List<AiUsageByUserVO> sumByUserAndModel(@Param("from") LocalDate from);

    /** Top 用户榜 */
    @Select("SELECT user_id AS userId,"
            + " SUM(request_count) AS requestCount,"
            + " SUM(prompt_tokens + completion_tokens) AS tokens,"
            + " SUM(cost) AS cost,"
            + " MAX(update_time) AS lastActive"
            + " FROM ai_usage_daily WHERE stat_date >= #{from}"
            + " GROUP BY user_id ORDER BY cost DESC LIMIT #{limit}")
    List<AiTopUserVO> topUsers(@Param("from") LocalDate from, @Param("limit") int limit);

    /**
     * 总量卡片：一次查询同时算今日与区间累计。
     *
     * <p>SQL 里不写 {@code stat_date = CURDATE()}：服务器时区与应用时区未必一致，
     * 日期由调用方按 Asia/Shanghai 算好再传进来。
     */
    @Select("SELECT"
            + " COALESCE(SUM(CASE WHEN stat_date = #{today} THEN request_count ELSE 0 END), 0) AS todayRequests,"
            + " COALESCE(SUM(CASE WHEN stat_date = #{today} THEN prompt_tokens + completion_tokens ELSE 0 END), 0) AS todayTokens,"
            + " COALESCE(SUM(CASE WHEN stat_date = #{today} THEN cost ELSE 0 END), 0) AS todayCost,"
            + " COALESCE(SUM(request_count), 0) AS totalRequests,"
            + " COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS totalTokens,"
            + " COALESCE(SUM(cost), 0) AS totalCost"
            + " FROM ai_usage_daily WHERE stat_date >= #{from}")
    AiUsageSummaryVO summary(@Param("today") LocalDate today, @Param("from") LocalDate from);
}
