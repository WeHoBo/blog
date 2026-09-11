package com.blog.front.cache;

import java.time.Duration;

/**
 * 文章相关缓存 key 与 TTL 的唯一出处。
 * <p>
 * 之前这些字符串散落在 Controller 与 Service 里，定时发布任务也需要在同一套 key 上失效，
 * 三处各写一遍字面量迟早会写歪，故集中到这里。
 */
public final class ArticleCacheKeys {

    private ArticleCacheKeys() {
    }

    /** 归档聚合结果 */
    public static final String ARCHIVE = "article:archive";

    /** 系列聚合结果 */
    public static final String SERIES = "article:series";

    /** 聚合缓存存活时间 */
    public static final Duration AGG_TTL = Duration.ofMinutes(10);

    /** 单篇文章详情缓存（含正文） */
    public static final Duration ARTICLE_TTL = Duration.ofMinutes(10);

    /** 单篇文章详情缓存 key */
    public static String article(Long id) {
        return "article:" + id;
    }

    /** 浏览量窗口增量计数器 key */
    public static String viewCount(Long id) {
        return "article:view:" + id;
    }
}
