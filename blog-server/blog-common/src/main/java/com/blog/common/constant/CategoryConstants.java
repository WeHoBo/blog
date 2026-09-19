package com.blog.common.constant;

/**
 * 分类相关的固定常量。
 *
 * <p>「未建档文章」是一个<b>系统内置分类</b>：
 * 文章没有设置分类时统一归属到它，好处有三点 ——
 * <ol>
 *   <li>前台列表/卡片永远能显示出一个分类名，不会出现空白或裸的「未分类」；</li>
 *   <li>它是数据库里的真实分类行，分类页的「文章数」按 category_id 聚合时才数得到这批文章；</li>
 *   <li>读者在分类侧边栏里能直接点进去看到这批文章。</li>
 * </ol>
 * 正因为它承担兜底职责，所以<b>不允许在后台被删除</b>。
 */
public final class CategoryConstants {

    private CategoryConstants() {
    }

    /** 无分类文章的归属分类名（写死，前台展示也用它兜底） */
    public static final String DEFAULT_CATEGORY_NAME = "未建档文章";

    /**
     * 默认分类的 URL 别名。刻意用固定值：
     * category.slug 上有唯一键，并发初始化时可以靠它兜底，避免建出两条「未建档文章」。
     */
    public static final String DEFAULT_CATEGORY_SLUG = "uncategorized";

    /** 默认分类排序值：给一个很大的数，保证它排在所有人工分类之后 */
    public static final int DEFAULT_CATEGORY_SORT = 9999;
}
