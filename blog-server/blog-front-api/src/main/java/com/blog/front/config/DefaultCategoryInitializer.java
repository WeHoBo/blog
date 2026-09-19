package com.blog.front.config;

import com.blog.common.constant.CategoryConstants;
import com.blog.common.mapper.ArticleMapper;
import com.blog.front.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时确保内置分类「未建档文章」存在，并把历史遗留的「没有分类」的文章归入它。
 *
 * <p>为什么要真的回填一次：老库里可能存在 {@code category_id IS NULL} 的文章。
 * 前台列表虽然能用默认名兜底显示，但「未建档文章」分类页的文章数是按 {@code category_id}
 * 聚合出来的 —— 不回填就会出现「列表里显示有这个分类、点进去却是空的」这种自相矛盾。
 * 该 UPDATE 是幂等的：第一次执行后库里就不再有 category_id 为 NULL 的行。
 *
 * <p>刻意<b>不</b>让失败中断启动：分类功能异常不应该阻止整个站点起来，记一条 warn 即可。
 * （与 {@link WordCountMigrator} 的「必须快速失败」不同 —— 那条是表结构缺失，会让所有查询 500。）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultCategoryInitializer implements ApplicationRunner {

    private final ArticleService articleService;
    private final ArticleMapper articleMapper;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Long defaultCategoryId = articleService.defaultCategoryId();
            int moved = articleMapper.assignNullCategory(defaultCategoryId);
            if (moved > 0) {
                log.info("已将 {} 篇没有分类的文章归入内置分类「{}」(id={})",
                        moved, CategoryConstants.DEFAULT_CATEGORY_NAME, defaultCategoryId);
            }
        } catch (Exception e) {
            log.warn("内置分类「{}」初始化失败，无分类文章将只在展示层兜底：{}",
                    CategoryConstants.DEFAULT_CATEGORY_NAME, e.getMessage());
        }
    }
}
