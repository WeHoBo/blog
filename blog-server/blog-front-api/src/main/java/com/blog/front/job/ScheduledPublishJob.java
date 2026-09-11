package com.blog.front.job;

import com.blog.front.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时发布任务：把 publish_at 已到期的草稿自动转为「已发布」。
 * <p>
 * 为什么用定时任务改状态，而不是在列表 / 详情查询上加 publish_at 过滤？
 * 因为后者要同时改列表、详情、归档、系列、sitemap 五条链路，任何一条漏掉都会
 * 出现「已发布但列表里没有」这类鬼故事；前者只写一处，其余查询自动正确。
 * <p>
 * 代价是发布时刻最多晚一个扫描周期（默认 60 秒），对博客场景完全可接受。
 * 扫描间隔可用 {@code BLOG_PUBLISH_SCAN_INTERVAL_MS} 覆盖，不配也有默认值。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishJob {

    private final ArticleService articleService;

    @Scheduled(initialDelayString = "${blog.publish-scan-initial-delay-ms:30000}",
            fixedDelayString = "${blog.publish-scan-interval-ms:60000}")
    public void promoteScheduledArticles() {
        try {
            int promoted = articleService.promoteScheduledArticles();
            if (promoted > 0) {
                log.info("定时发布：{} 篇文章已到期，自动转为已发布", promoted);
            }
        } catch (Exception e) {
            // 单次失败不能让调度线程退出，否则后续所有定时发布都会静默停摆
            log.error("定时发布任务执行失败，将在下个周期重试", e);
        }
    }
}
