package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.constant.CategoryConstants;
import com.blog.common.dto.ArticleDTO;
import com.blog.common.dto.AuthorArticleDTO;
import com.blog.common.entity.Article;
import com.blog.common.entity.ArticleRevision;
import com.blog.common.entity.ArticleTag;
import com.blog.common.entity.Category;
import com.blog.common.entity.Tag;
import com.blog.common.exception.BusinessException;
import com.blog.common.mapper.ArticleMapper;
import com.blog.common.mapper.ArticleRevisionMapper;
import com.blog.common.mapper.ArticleTagMapper;
import com.blog.common.mapper.CategoryMapper;
import com.blog.common.mapper.TagMapper;
import com.blog.common.vo.TagArticleCount;
import com.blog.front.cache.ArticleCacheKeys;
import com.blog.front.util.DocxToMdUtil;
import com.blog.front.util.FrontMatter;
import com.blog.front.util.WordCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    /** 单篇文章最多保留的历史版本数，超出即淘汰最旧的 */
    private static final int MAX_REVISIONS = 30;

    /**
     * 关键词命中标签/分类时最多回填的 id 数量。
     * 不加限制的话，命中的标签一旦挂着上千篇文章，就会拼出一条几千项的 IN 列表，
     * MySQL 解析成本随项数线性上升，还会撑爆 SQL 长度上限。
     */
    private static final int SEARCH_RELATION_ID_LIMIT = 500;

    /**
     * 自动保存模式下的快照节流间隔。
     * 编辑器每 30 秒自动存一次，若每次都留快照，30 个版本只能覆盖十几分钟的写作过程；
     * 因此自动保存最多每 10 分钟留一版，而用户主动「保存 / 发布」时**必定**留一版。
     */
    private static final Duration AUTOSAVE_SNAPSHOT_INTERVAL = Duration.ofMinutes(10);

    private static final DateTimeFormatter FRONT_MATTER_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleRevisionMapper articleRevisionMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    /** 文章从公开变为非公开时，顺手把知识库里的向量也撤掉 */
    private final RagIndexClient ragIndexClient;

    // =================================================================
    // 列表 / 详情
    // =================================================================

    /** 兼容旧调用方：不关心「精品 / 置顶」两个维度时用这个重载（归档/分类/标签/检索工具都走它） */
    public Page<Article> page(int pageNum, int pageSize, Long categoryId, Long tagId, String keyword, List<Long> categoryIds, String sort) {
        return page(pageNum, pageSize, categoryId, tagId, keyword, categoryIds, sort, null, null);
    }

    /** 只按精品维度过滤（保留给「精品」相关查询，首页已改用置顶维度） */
    public Page<Article> page(int pageNum, int pageSize, Long categoryId, Long tagId, String keyword,
                              List<Long> categoryIds, String sort, Integer featured) {
        return page(pageNum, pageSize, categoryId, tagId, keyword, categoryIds, sort, featured, null);
    }

    /**
     * 公开文章分页。
     *
     * @param featured 精品（精品）过滤：1 只取 / 0 排除 / null 不限。**首页不再用它**，
     *                 现在它只在后台与「精品」徽标相关的地方出现。
     * @param top      置顶过滤：1 只取置顶 / 0 排除置顶 / null 不限。
     *                 首页靠它分工：中间那栏取 {@code 1}，右侧「全部文章」取 {@code 0} ——
     *                 置顶文章只在中间出现一次，右侧不重复展示。
     */
    public Page<Article> page(int pageNum, int pageSize, Long categoryId, Long tagId, String keyword,
                              List<Long> categoryIds, String sort, Integer featured, Integer top) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0);
        if (featured != null) {
            wrapper.eq(Article::getIsFeatured, featured);
        }
        if (top != null) {
            wrapper.eq(Article::getIsTop, top);
        }
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(Article::getCategoryId, categoryIds);
        } else if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            applyKeywordSearch(wrapper, keyword.trim());
        }
        if (tagId != null) {
            List<ArticleTag> ats = articleTagMapper.selectList(
                    new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
            List<Long> ids = ats.stream().map(ArticleTag::getArticleId).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                wrapper.in(Article::getId, ids);
            } else {
                wrapper.eq(Article::getId, -1L);
            }
        }
        if ("title".equals(sort)) {
            // 标题正序。原先用 CAST(title AS UNSIGNED)，非纯数字标题一律被转成 0，
            // 排序结果错乱（中文标题全部并列）。这里回归可预期的字符串排序。
            wrapper.orderByDesc(Article::getIsTop)
                    .orderByAsc(Article::getTitle);
        } else {
            wrapper.orderByDesc(Article::getIsTop)
                    .orderByDesc(Article::getCreateTime);
        }
        // 列表只需要展示字段，显式排除 content_md / content_html 两个 LONGTEXT，
        // 否则每次翻页都会把整页文章的正文全文从数据库读回内存
        wrapper.select(Article::getId, Article::getTitle, Article::getSlug, Article::getSummary, Article::getCover,
                Article::getViewCount, Article::getCommentCount, Article::getIsTop,
                // 精品标记要一起查出来：首页中间那栏靠它渲染「精品」徽标
                Article::getIsFeatured,
                Article::getCategoryId, Article::getCreateTime, Article::getWordCount,
                // 列表卡片上的日期优先显示「发布时间」，所以要把它一起查出来
                Article::getPublishAt,
                Article::getUserId);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 关键词搜索：标题 + 摘要 + 标签名 + 分类名。
     *
     * <p>后两类命中没法直接在 article 表上 LIKE —— 标签在 article_tag 关联表、分类在 category 表，
     * 所以先查出命中的 id 集合，再以 {@code OR id IN (...)} / {@code OR category_id IN (...)} 并入。
     *
     * <p>整组必须用 {@code and(...)} 包起来：外层已经有 {@code status=1}、{@code is_deleted=0} 条件，
     * 如果直接在外层 {@code or()}，就会变成
     * {@code status=1 AND is_deleted=0 AND title LIKE ? OR ...}，
     * 后半个 OR 会把前两个条件短路掉 —— 草稿和已删除文章都会漏到前台搜索结果里。
     */
    private void applyKeywordSearch(LambdaQueryWrapper<Article> wrapper, String keyword) {
        List<Long> tagArticleIds = articleIdsByTagKeyword(keyword);
        List<Long> categoryIds = categoryIdsByKeyword(keyword);
        wrapper.and(w -> {
            w.like(Article::getTitle, keyword)
                    .or().like(Article::getSummary, keyword);
            if (!categoryIds.isEmpty()) {
                w.or().in(Article::getCategoryId, categoryIds);
            }
            if (!tagArticleIds.isEmpty()) {
                w.or().in(Article::getId, tagArticleIds);
            }
        });
    }

    /** 标签名命中 → 这些标签下的文章 id（去重并限长） */
    private List<Long> articleIdsByTagKeyword(String keyword) {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().like(Tag::getName, keyword));
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = tags.stream().map(Tag::getId).filter(Objects::nonNull)
                .distinct().limit(SEARCH_RELATION_ID_LIMIT).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        List<ArticleTag> links = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getTagId, tagIds));
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        return links.stream()
                .map(ArticleTag::getArticleId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(SEARCH_RELATION_ID_LIMIT)
                .toList();
    }

    /** 分类名命中 → 这些分类的 id（去重并限长） */
    private List<Long> categoryIdsByKeyword(String keyword) {
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().like(Category::getName, keyword));
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .map(Category::getId)
                .filter(Objects::nonNull)
                .distinct()
                .limit(SEARCH_RELATION_ID_LIMIT)
                .toList();
    }

    public Page<Article> adminPage(int pageNum, int pageSize, String keyword, Integer status, Long categoryId) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDeleted, 0);
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }
        wrapper.orderByDesc(Article::getIsTop)
                .orderByDesc(Article::getCreateTime);
        // 后台列表同样只取元信息：排除 content_md / content_html 两个正文大字段
        wrapper.select(Article::getId, Article::getTitle, Article::getSlug, Article::getSummary,
                Article::getCover, Article::getStatus, Article::getPublishAt, Article::getUserId,
                // 待审列表要能直接看到驳回理由（作者也靠它知道该改什么）
                Article::getRejectReason,
                Article::getCategoryId, Article::getSeries, Article::getIsTop, Article::getViewCount,
                // 后台列表要显示「[精品]」徽标与「取消精品」按钮，必须把标记查出来
                Article::getIsFeatured,
                Article::getCommentCount, Article::getLikeCount, Article::getWordCount,
                Article::getIsDeleted,
                Article::getCreateTime, Article::getUpdateTime);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Article getPublicById(Long id) {
        return getPublicById(id, true);
    }

    /**
     * 取前台文章详情。
     *
     * @param countView 是否计入阅读数。
     *                  <p>页面 HTML 上了 routeRules 缓存之后，同一个 URL 在缓存窗口内只会渲染一次，
     *                  如果还按「每次 GET 都 +1」，缓存命中期间的阅读就全丢了。
     *                  所以服务端渲染那条路径传 false（只读展示值），改由浏览器挂载后调
     *                  {@code POST /api/article/{id}/view} 单独打点 —— 这样缓存命中/未命中都是一次访问记一次。
     *                  App 等直接调接口的客户端不认这个约定，仍走 true，行为与之前一致。
     */
    public Article getPublicById(Long id, boolean countView) {
        Article article = loadPublicArticle(id);
        if (countView) {
            applyViewCount(id, article);
        } else {
            // 只读：把「缓存里的基准值 + 窗口内增量」算出来展示，不写库也不自增
            article.setViewCount(currentViewCount(id, article.getViewCount()));
        }
        return article;
    }

    /**
     * 按 slug 取公开文章（AI 工具用）。
     *
     * <p>不进缓存：slug 只在模型偶尔按 URL 反查时用到，命中率低，没必要占缓存位。
     * 但**必须**走 {@link #isPublicArticle} 判断，否则模型能通过 slug 读到草稿。
     */
    public Article getPublicBySlug(String slug) {
        if (StrUtil.isBlank(slug)) {
            throw new BusinessException(404, "文章不存在");
        }
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getSlug, slug.trim())
                .last("LIMIT 1"));
        if (article == null || !isPublicArticle(article)) {
            throw new BusinessException(404, "文章不存在");
        }
        return article;
    }

    /**
     * 缓存优先地取一篇公开文章（不含阅读数逻辑）。
     * <p>
     * Redis 故障不能拖垮阅读：读缓存失败就回源数据库，写缓存失败只丢缓存不丢请求。
     * 聚合缓存（archive / series）一直有这个兜底，详情链路 2026-11-23 才补齐 ——
     * 在此之前 Redis 一挂，所有文章详情直接 500，而数据库其实好好的。
     */
    private Article loadPublicArticle(Long id) {
        String key = ArticleCacheKeys.article(id);
        Article cached = null;
        try {
            cached = (Article) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("[文章详情] 缓存读取失败，回源数据库 id={}: {}", id, e.toString());
        }
        if (cached != null) {
            if (!isPublicArticle(cached)) {
                try {
                    redisTemplate.delete(key);
                } catch (Exception ignored) {
                    // 缓存删不掉不影响判定：下面照样按 404 处理
                }
                throw new BusinessException(404, "文章不存在");
            }
            return cached;
        }
        Article article = articleMapper.selectById(id);
        if (article == null || !isPublicArticle(article)) {
            throw new BusinessException(404, "文章不存在");
        }
        try {
            redisTemplate.opsForValue().set(key, article, ArticleCacheKeys.ARTICLE_TTL);
            redisTemplate.opsForValue().set(ArticleCacheKeys.viewCount(id), 0,
                    ArticleCacheKeys.ARTICLE_TTL);
        } catch (Exception e) {
            log.warn("[文章详情] 缓存写入失败，本次不回填 id={}: {}", id, e.toString());
        }
        return article;
    }

    /**
     * 阅读数 +1，并把自增后的值写回传入的 article。
     * <p>
     * ⚠️ 以前是「DB 值 + Redis 窗口增量」，那是重复计数：
     * 传进来的 article 来自 selectById，是**自增前的**最新 DB 值，
     * 再从 key 创建起累计一个 delta 加上去，同一段时间被数了两遍。
     * 具体表现（DB=1000，10 分钟内访问 20 次）：第 20 次显示 1019，实际 1020。
     * 而且 ARTICLE_TTL 到期 delta 归零，阅读数会往回跳 —— 用户能看见。
     * <p>
     * 正确的口径：selectById 读到的是自增前的值，+1 就是自增后的值。
     * Redis 计数器仍然要自增，但只服务于 {@link #currentViewCount} ——
     * 那条走的是缓存快照 base + 窗口增量，口径本身是对的。
     * <p>
     * 已知代价：并发下两个请求读到同一个 base，显示会少 1（DB 侧仍准确）。
     * 要绝对准确得在自增后回查一次单列，个人博客不值得多这一次查询。
     */
    private void applyViewCount(Long id, Article article) {
        Integer before = article.getViewCount();
        // 持久化：单条主键 UPDATE 原子自增（数据库始终是浏览量的真实来源）
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .setSql("view_count = IFNULL(view_count, 0) + 1"));
        // Redis 计数器照常自增：currentViewCount 靠它给缓存快照算增量，断了那边就静止
        redisTemplate.opsForValue().increment(ArticleCacheKeys.viewCount(id));
        article.setViewCount((before == null ? 0 : before) + 1);
    }

    /** 只读地算出当前展示值（基准值 + Redis 窗口增量），不写库、不自增 */
    private int currentViewCount(Long id, Integer base) {
        int value = base == null ? 0 : base;
        try {
            Object delta = redisTemplate.opsForValue().get(ArticleCacheKeys.viewCount(id));
            if (delta instanceof Number n) {
                return value + n.intValue();
            }
        } catch (Exception ignored) {
            // Redis 故障时退回基准值，不影响详情可用性
        }
        return value;
    }

    /**
     * 阅读数打点（浏览器侧调用）。
     * 返回最新阅读数，前端可以就地刷新数字而不用整页重载。
     */
    public int incrementViewCount(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || !isPublicArticle(article)) {
            throw new BusinessException(404, "文章不存在");
        }
        applyViewCount(id, article);
        return article.getViewCount() == null ? 0 : article.getViewCount();
    }

    public Article getAdminById(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return article;
    }

    private boolean isPublicArticle(Article article) {
        return article.getStatus() != null && article.getStatus() == 1;
    }

    // =================================================================
    // 新建 / 更新
    // =================================================================

    /**
     * 当前请求是不是超级管理员。
     * <p>超管在 JWT 过滤器里会同时拿到 {@code ROLE_admin} 与 {@code ROLE_super}
     * （见 {@code JwtAuthenticationFilter#authoritiesOf}），所以判 {@code ROLE_super} 即准。
     */
    private boolean isSuper() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_super".equals(a.getAuthority()));
    }

    /**
     * 「精品」字段的授权闸门 —— 只有超管能改。
     *
     * <p>⚠️ 为什么非超管传入时是**静默丢弃**而不是抛错：
     * 普通管理员编辑一篇"已由超管设为精品"的文章时，若前端把原值一起提交上来，
     * 抛错会导致他永远改不了这篇（保存直接失败）。丢弃的语义是"这次请求对精品无效"，
     * 原值保持不变 —— 伪造请求同样达不到目的，但不会误伤正常编辑。
     *
     * @return 允许写入的值；{@code null} 表示"不改动"
     */
    private Integer gateFeatured(Integer requested) {
        if (requested == null) {
            return null;
        }
        if (isSuper()) {
            return Integer.valueOf(1).equals(requested) ? 1 : 0;
        }
        log.warn("非超管尝试设置精品字段，已忽略：requested={}", requested);
        return null;
    }

    @Transactional
    public Article create(ArticleDTO dto, Long userId) {
        // 先确定 slug 再落库：宁可提前给出「该 URL 已被占用」，也不要抛唯一键冲突
        String slug = StrUtil.isNotBlank(dto.getSlug()) ? dto.getSlug().trim() : generateSlug(dto.getTitle());
        ensureSlugAvailable(slug, null);

        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setSlug(slug);
        article.setContentMd(dto.getContentMd());
        article.setContentHtml(dto.getContentHtml());
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        // 没选分类也不算「没分类」：统一归到内置的「未建档文章」，避免出现裸的 null
        Long categoryId = dto.getCategoryId() != null ? dto.getCategoryId() : defaultCategoryId();
        article.setCategoryId(categoryId);
        article.setSeries(dto.getSeries());
        article.setIsTop(dto.getIsTop() != null ? dto.getIsTop() : 0);
        // 精品只能由超管设置：非超管传上来的值在这里被丢弃，落库恒为 0
        Integer featured = gateFeatured(dto.getIsFeatured());
        article.setIsFeatured(featured != null ? featured : 0);
        article.setWordCount(WordCounter.count(dto.getContentMd()));
        article.setUserId(userId);
        applyPublishSchedule(article, dto, null);
        requireContentWhenPublished(article);

        try {
            articleMapper.insert(article);
        } catch (DuplicateKeyException e) {
            // 并发下的兜底：两个请求同时通过了上面的检查
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }

        syncTags(article.getId(), dto.getTagIds());
        adjustCategoryCount(categoryId, 1);
        return article;
    }

    /**
     * 更新文章。
     *
     * @param editorId 本次修改人（记入版本快照）
     * @param autosave 是否来自自动保存：自动保存时快照按 {@link #AUTOSAVE_SNAPSHOT_INTERVAL} 节流，
     *                 用户主动保存则必定留一版
     */
    @Transactional
    public Article update(Long id, ArticleDTO dto, Long editorId, boolean autosave) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        // 原状态要在 applyPublishSchedule 覆盖之前抓住 —— 它决定「这次改动是不是审核流程的一部分」
        Integer originalStatus = article.getStatus();
        String oldTitle = article.getTitle();
        Long oldCategoryId = article.getCategoryId();

        // 正文用「不传即不改」的语义：避免第三方调用漏传 contentMd 时把正文清空
        String nextContent = dto.getContentMd() != null ? dto.getContentMd() : article.getContentMd();

        // 覆盖之前先留一版快照，之后才动数据库
        maybeSnapshot(article, dto, editorId, autosave);

        article.setTitle(dto.getTitle());
        article.setContentMd(nextContent);
        article.setContentHtml(dto.getContentHtml());
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        // 同 create：清空分类也归入「未建档文章」，而不是留一个 null
        Long nextCategoryId = dto.getCategoryId() != null ? dto.getCategoryId() : defaultCategoryId();
        article.setCategoryId(nextCategoryId);
        article.setSeries(dto.getSeries());
        article.setWordCount(WordCounter.count(nextContent));
        if (dto.getIsTop() != null) {
            article.setIsTop(dto.getIsTop());
        }
        // 精品：不传 = 不改动（普通管理员的表单不会带这个字段，
        // 否则他编辑一篇精品文章就会把超管设的标记抹掉）
        Integer featured = gateFeatured(dto.getIsFeatured());
        if (featured != null) {
            article.setIsFeatured(featured);
        }

        // slug 策略：
        //   1) 显式传了 slug → 用它（先查重）
        //   2) 没传，且文章还是草稿、标题又变了 → 跟随标题重新生成
        //   3) 其余情况一律保持原 slug —— 已发布文章的 URL 不能悄悄变，
        //      否则外链与搜索引擎收录会全部 404
        String slug = article.getSlug();
        if (StrUtil.isNotBlank(dto.getSlug())) {
            slug = dto.getSlug().trim();
        } else if (isDraft(article) && !Objects.equals(oldTitle, dto.getTitle())) {
            slug = generateSlug(dto.getTitle());
        }
        ensureSlugAvailable(slug, id);
        article.setSlug(slug);

        applyPublishSchedule(article, dto, originalStatus);
        // 从公开变成不公开（撤下 / 转草稿 / 送审）时，知识库里的向量也要撤掉。
        // ⚠️ 只在「状态真的变了」时才调：草稿的自动保存每 5 秒一次，
        // 无条件调等于给 RAG 挂了一个 5 秒一次的定时请求。
        if (originalStatus != null && originalStatus == STATUS_PUBLISHED
                && (article.getStatus() == null || article.getStatus() != STATUS_PUBLISHED)) {
            afterCommit(() -> ragIndexClient.evictArticle(id));
        }
        requireContentWhenPublished(article);

        try {
            articleMapper.updateById(article);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }
        // ⚠️ publishAt 需要被清空时（撤下、取消定时、转纯草稿），上面那条 updateById
        // 根本不会写它：MyBatis-Plus 默认更新策略是 NOT_NULL，实体里为 null 的字段
        // 直接不进 SET 子句。结果是数据库里 publish_at 还留着原值，而
        // ScheduledPublishJob 的条件正是「status=0 且 publish_at <= now()」——
        // 一篇刚撤下的文章会在下一分钟被它重新公开（2026-11-23 修）。
        // 只能用显式 SQL 置空（setSql 而不是 set(..., null)，后者依赖包装行为，容易看走眼）。
        if (article.getPublishAt() == null) {
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, id)
                    .setSql("publish_at = NULL"));
        }

        syncTags(id, dto.getTagIds());
        if (!Objects.equals(oldCategoryId, nextCategoryId)) {
            adjustCategoryCount(oldCategoryId, -1);
            adjustCategoryCount(nextCategoryId, 1);
        }
        evictArticleCache(id);
        return article;
    }

    // =================================================================
    // 作者（author）路径
    // -----------------------------------------------------------------
    // 为什么另起一套，而不是给 update() 加个参数：
    //   后台管理员那套逻辑里混着「定时发布 / 置顶 / 精品」这些只有超管能碰的东西，
    //   把作者塞进去就得在同一个方法里到处判角色，漏一处就是越权。
    //   单独一条路径 + 单独的 DTO，边界一眼可见，也不必动那 37 个 admin 注解。
    // =================================================================

    /** 状态常量：0 草稿 / 1 已发布 / 2 待审 / 3 已驳回。只有 1 是公开的。 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_PENDING = 2;
    public static final int STATUS_REJECTED = 3;

    /** 驳回理由最大长度，与 article.reject_reason 列宽保持一致 */
    private static final int REJECT_REASON_MAX = 500;

    /**
     * 作者自己的文章列表。
     *
     * <p>⚠️ userId 由 controller 从登录态取、**不接受前端传**：
     * 一旦允许传，把它换成别人的 id 就成了「看别人的草稿」。
     */
    public Page<Article> authorPage(Long userId, int pageNum, int pageSize, String keyword, Integer status) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getIsDeleted, 0)
                .eq(Article::getUserId, userId);
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Article::getTitle, keyword);
        }
        wrapper.orderByDesc(Article::getUpdateTime)
                .select(Article::getId, Article::getTitle, Article::getSlug, Article::getSummary,
                        Article::getCover, Article::getStatus, Article::getRejectReason,
                        Article::getPublishAt, Article::getCategoryId, Article::getWordCount,
                        Article::getViewCount, Article::getCreateTime, Article::getUpdateTime);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 作者读自己的某一篇（编辑页回填正文）。不属于自己一律拒绝，避免"能读别人的草稿"。 */
    public Article getOwnArticle(Long id, Long userId) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        if (userId == null || !Objects.equals(article.getUserId(), userId)) {
            throw new BusinessException(403, "这不是你的文章");
        }
        return article;
    }

    /**
     * 作者新建文章。
     *
     * @param submit true=提交审核（status=2），false=仅存草稿（status=0）
     *
     * <p>⚠️ 落库时这几个值**硬写**，不看调用方传什么：
     * userId=当前用户、isTop=0、isFeatured=0、**publishAt=null**。
     *
     * <p>publishAt 必须为 null —— 定时发布任务的筛选条件是
     * 「status=0 且 publish_at 不为空且已到期」，作者的文章永远没有 publish_at，
     * 就永远不可能被自动发布，待审状态也就绕不过去。
     */
    @Transactional
    public Article createForAuthor(AuthorArticleDTO dto, Long userId, boolean submit) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String content = StrUtil.blankToDefault(dto.getContentMd(), "");
        if (submit && StrUtil.isBlank(content)) {
            throw new BusinessException(400, "提交审核前请先写正文");
        }

        String slug = StrUtil.isNotBlank(dto.getSlug()) ? dto.getSlug().trim() : generateSlug(dto.getTitle());
        ensureSlugAvailable(slug, null);

        Article article = new Article();
        article.setTitle(dto.getTitle());
        article.setSlug(slug);
        article.setContentMd(content);
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        article.setCategoryId(dto.getCategoryId() != null ? dto.getCategoryId() : defaultCategoryId());
        article.setWordCount(WordCounter.count(content));
        article.setUserId(userId);
        article.setStatus(submit ? STATUS_PENDING : STATUS_DRAFT);
        article.setRejectReason("");
        article.setIsTop(0);
        article.setIsFeatured(0);
        article.setPublishAt(null);

        try {
            articleMapper.insert(article);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }
        syncTags(article.getId(), dto.getTagIds());
        adjustCategoryCount(article.getCategoryId(), 1);
        return article;
    }

    /**
     * 作者修改自己的文章。
     *
     * <p>状态怎么走：
     * <ul>
     *   <li>草稿(0) / 已驳回(3) + 存草稿 → 仍是 0</li>
     *   <li>草稿(0) / 已驳回(3) + 提交审核 → 2，并清空驳回理由</li>
     *   <li>待审(2) → 保持 2（改完还是等审）</li>
     *   <li>已发布(1) → 保持 1，且 <b>publish_at 不动</b>，改完直接生效</li>
     * </ul>
     *
     * <p>「改完直接生效」是产品决定：审核只管第一次上屏。代价是作者能绕过审核改线上内容，
     * 兜底靠 {@link #forceSnapshot} 留下的版本快照。
     */
    @Transactional
    public Article updateForAuthor(Long id, AuthorArticleDTO dto, Long userId, boolean submit) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        // ⚠️ 归属校验必须在最前面：这条路径没有资源级的权限注解，
        // 光靠「当前用户是 author」挡不住「改别人的文章」。
        if (userId == null || !Objects.equals(article.getUserId(), userId)) {
            throw new BusinessException(403, "这不是你的文章，无权修改");
        }

        int current = article.getStatus() == null ? STATUS_DRAFT : article.getStatus();
        String oldTitle = article.getTitle();
        Long oldCategoryId = article.getCategoryId();
        String nextContent = dto.getContentMd() != null ? dto.getContentMd() : article.getContentMd();
        if (submit && StrUtil.isBlank(nextContent)) {
            throw new BusinessException(400, "提交审核前请先写正文");
        }

        // 正文变了才留快照（同管理员路径的口径，只是不做 autosave 节流）
        if (!Objects.equals(article.getContentMd(), nextContent)) {
            forceSnapshot(article, userId);
        }

        article.setTitle(dto.getTitle());
        article.setContentMd(nextContent);
        article.setSummary(dto.getSummary());
        article.setCover(dto.getCover());
        Long nextCategoryId = dto.getCategoryId() != null ? dto.getCategoryId() : defaultCategoryId();
        article.setCategoryId(nextCategoryId);
        article.setWordCount(WordCounter.count(nextContent));

        // slug 策略与后台一致：只有草稿会跟随标题重生成，待审/已发布一律不动 URL，
        // 否则外链和搜索引擎收录会 404
        String slug = article.getSlug();
        if (StrUtil.isNotBlank(dto.getSlug())) {
            slug = dto.getSlug().trim();
        } else if (current == STATUS_DRAFT && !Objects.equals(oldTitle, dto.getTitle())) {
            slug = generateSlug(dto.getTitle());
        }
        ensureSlugAvailable(slug, id);
        article.setSlug(slug);

        if (current == STATUS_PUBLISHED) {
            // 已发布：状态与发布时间都不动，改完直接生效
            article.setStatus(STATUS_PUBLISHED);
        } else if (submit) {
            article.setStatus(STATUS_PENDING);
            // 重新提交后旧理由就该消失（置空串而非 null：updateById 默认忽略 null 字段）
            article.setRejectReason("");
        } else {
            article.setStatus(current == STATUS_PENDING ? STATUS_PENDING : STATUS_DRAFT);
        }

        try {
            articleMapper.updateById(article);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }
        syncTags(id, dto.getTagIds());
        if (!Objects.equals(oldCategoryId, nextCategoryId)) {
            adjustCategoryCount(oldCategoryId, -1);
            adjustCategoryCount(nextCategoryId, 1);
        }
        evictArticleCache(id);
        return article;
    }

    /**
     * 作者删自己的文章：**只允许草稿(0) 和已驳回(3)**。
     * 待审的撤不回来（找超管），已发布的只有超管能删。
     */
    @Transactional
    public void deleteForAuthor(Long id, Long userId) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        if (userId == null || !Objects.equals(article.getUserId(), userId)) {
            throw new BusinessException(403, "这不是你的文章，无权删除");
        }
        int status = article.getStatus() == null ? STATUS_DRAFT : article.getStatus();
        if (status != STATUS_DRAFT && status != STATUS_REJECTED) {
            throw new BusinessException(400, "只有草稿和已驳回的文章可以删除，待审或已发布的请联系管理员");
        }
        delete(id);
    }

    /** 超管审核通过：置为已发布，发布时间 = 此刻（不采信作者选的时间）。 */
    @Transactional
    public Article approve(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!Integer.valueOf(STATUS_PENDING).equals(article.getStatus())) {
            throw new BusinessException(400, "这篇文章不在待审状态");
        }
        Article patch = new Article();
        patch.setId(id);
        patch.setStatus(STATUS_PUBLISHED);
        patch.setPublishAt(LocalDateTime.now());
        patch.setRejectReason("");
        articleMapper.updateById(patch);
        evictArticleCache(id);
        return articleMapper.selectById(id);
    }

    /** 超管驳回：置为 3 并留下理由，作者改完可以重新提交。 */
    @Transactional
    public Article reject(Long id, String reason) {
        Article article = articleMapper.selectById(id);
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        if (!Integer.valueOf(STATUS_PENDING).equals(article.getStatus())) {
            throw new BusinessException(400, "这篇文章不在待审状态");
        }
        String text = StrUtil.blankToDefault(reason, "").trim();
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(400, "请填写驳回理由，作者需要知道该改什么");
        }
        if (text.length() > REJECT_REASON_MAX) {
            text = text.substring(0, REJECT_REASON_MAX);
        }
        Article patch = new Article();
        patch.setId(id);
        patch.setStatus(STATUS_REJECTED);
        patch.setRejectReason(text);
        articleMapper.updateById(patch);
        return articleMapper.selectById(id);
    }

    /** 软删除（进回收站）。刻意保留 article_tag：从回收站恢复时标签才跟着回来。 */
    @Transactional
    public void delete(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        articleMapper.deleteById(id);
        adjustCategoryCount(article.getCategoryId(), -1);
        evictArticleCache(id);
    }

    /**
     * 直接设置 / 取消「精品」。
     *
     * <p>后台文章列表的一键操作走这里，**不经过整篇保存**：
     * 只改一个展示位标记，没必要触发版本快照（否则「设为精品」会在版本历史里留一版空改动）。
     *
     * <p>只更新非 null 字段（MyBatis-Plus 默认策略），所以不会碰正文与其它元信息。
     */
    @Transactional
    public Article setFeatured(Long id, Integer featured) {
        if (!isSuper()) {
            throw new BusinessException(403, "只有超级管理员可以设置精品文章");
        }
        Article article = articleMapper.selectById(id);
        // is_deleted 是本项目手工维护的逻辑删除，MyBatis-Plus 不会自动过滤，得自己判
        if (article == null || Integer.valueOf(1).equals(article.getIsDeleted())) {
            throw new BusinessException(404, "文章不存在");
        }
        Article patch = new Article();
        patch.setId(id);
        patch.setIsFeatured(Integer.valueOf(1).equals(featured) ? 1 : 0);
        articleMapper.updateById(patch);
        evictArticleCache(id);
        article.setIsFeatured(patch.getIsFeatured());
        return article;
    }

    private boolean isDraft(Article article) {
        return article.getStatus() == null || article.getStatus() == 0;
    }

    // =================================================================
    // 回收站
    // =================================================================

    /** 回收站分页（MyBatis-Plus 的逻辑删除会自动过滤已删行，故走原生 SQL） */
    public Map<String, Object> trashPage(int pageNum, int pageSize, String keyword) {
        int page = Math.max(1, pageNum);
        int size = Math.min(Math.max(1, pageSize), 100);
        List<Article> records = articleMapper.selectTrashPage(keyword, (page - 1) * size, size);
        long total = articleMapper.countTrash(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("pages", (total + size - 1) / size);
        result.put("current", page);
        return result;
    }

    /** 从回收站恢复 */
    @Transactional
    public void restoreFromTrash(Long id) {
        // selectById 受逻辑删除过滤，已删的行查不到，所以恢复走原生 SQL
        int affected = articleMapper.restoreById(id);
        if (affected == 0) {
            throw new BusinessException(404, "文章不在回收站中");
        }
        Article restored = articleMapper.selectById(id);
        adjustCategoryCount(restored == null ? null : restored.getCategoryId(), 1);
        evictArticleCache(id);
    }

    /** 彻底删除：物理删行，同时清掉标签关联与版本快照，避免留下永久孤儿数据 */
    @Transactional
    public void forceDelete(Long id) {
        int affected = articleMapper.forceDeleteById(id);
        if (affected == 0) {
            // 区分两种 0 行：文章压根不存在 vs 它已经不在回收站了。
            // 后者是并发场景 —— 有人刚把它恢复，或者别人已经删过一遍。
            // 以前这里只有 WHERE id，所以「已恢复」的正文会被旧列表的删除操作带走，
            // 而且用户看到的是「彻底删除成功」。
            if (articleMapper.selectById(id) != null) {
                throw new BusinessException(409, "这篇文章已经不在回收站了（可能刚被恢复），请刷新列表再看");
            }
            throw new BusinessException(404, "文章不存在");
        }
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        articleRevisionMapper.delete(new LambdaQueryWrapper<ArticleRevision>()
                .eq(ArticleRevision::getArticleId, id));
        evictArticleCache(id);
    }

    // =================================================================
    // 版本历史
    // =================================================================

    public List<ArticleRevision> revisions(Long articleId, int limit) {
        int capped = Math.min(Math.max(1, limit), MAX_REVISIONS);
        return articleRevisionMapper.selectMetaList(articleId, capped);
    }

    public ArticleRevision revisionDetail(Long articleId, Long revisionId) {
        ArticleRevision revision = articleRevisionMapper.selectById(revisionId);
        if (revision == null || !Objects.equals(revision.getArticleId(), articleId)) {
            throw new BusinessException(404, "该版本不存在");
        }
        return revision;
    }

    /**
     * 回滚到指定版本。
     * <p>
     * 回滚前会把「当前版本」也存成一份快照，所以回滚本身也是可回滚的。
     * 刻意**不恢复 slug**：旧版本的 slug 可能已被别的文章占用，且改 slug 会让现有外链失效。
     */
    @Transactional
    public Article restoreRevision(Long articleId, Long revisionId, Long editorId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        ArticleRevision revision = revisionDetail(articleId, revisionId);

        forceSnapshot(article, editorId);

        Long oldCategoryId = article.getCategoryId();
        article.setTitle(revision.getTitle());
        article.setContentMd(revision.getContentMd());
        article.setSummary(revision.getSummary());
        article.setCover(revision.getCover());
        // 版本快照里可能也是空分类（历史数据），同样兜底到「未建档文章」
        Long restoredCategoryId = revision.getCategoryId() != null
                ? revision.getCategoryId() : defaultCategoryId();
        article.setCategoryId(restoredCategoryId);
        article.setSeries(revision.getSeries());
        article.setWordCount(WordCounter.count(revision.getContentMd()));
        articleMapper.updateById(article);

        if (!Objects.equals(oldCategoryId, restoredCategoryId)) {
            adjustCategoryCount(oldCategoryId, -1);
            adjustCategoryCount(restoredCategoryId, 1);
        }
        evictArticleCache(articleId);
        return article;
    }

    /**
     * 保存快照。仅在「正文真的变了」时留一版，避免反复点保存塞满无意义的版本。
     */
    private void maybeSnapshot(Article current, ArticleDTO dto, Long editorId, boolean autosave) {
        String next = dto.getContentMd() != null ? dto.getContentMd() : current.getContentMd();
        if (Objects.equals(current.getContentMd(), next)) {
            return;
        }
        if (autosave) {
            LocalDateTime latest = articleRevisionMapper.selectLatestTime(current.getId());
            if (latest != null && latest.isAfter(LocalDateTime.now().minus(AUTOSAVE_SNAPSHOT_INTERVAL))) {
                return;
            }
        }
        forceSnapshot(current, editorId);
    }

    private void forceSnapshot(Article current, Long editorId) {
        ArticleRevision revision = new ArticleRevision();
        revision.setArticleId(current.getId());
        revision.setTitle(current.getTitle());
        revision.setSlug(current.getSlug());
        revision.setContentMd(current.getContentMd());
        revision.setSummary(current.getSummary());
        revision.setCover(current.getCover());
        revision.setCategoryId(current.getCategoryId());
        revision.setSeries(current.getSeries());
        revision.setStatus(current.getStatus());
        revision.setWordCount(current.getWordCount());
        revision.setEditorId(editorId);
        articleRevisionMapper.insert(revision);
        pruneRevisions(current.getId());
    }

    /** 版本数超上限时淘汰最旧的，只删本次超出的部分 */
    private void pruneRevisions(Long articleId) {
        List<ArticleRevision> metas = articleRevisionMapper.selectMetaList(articleId, MAX_REVISIONS + 1);
        if (metas.size() <= MAX_REVISIONS) {
            return;
        }
        List<Long> stale = metas.subList(MAX_REVISIONS, metas.size()).stream()
                .map(ArticleRevision::getId)
                .collect(Collectors.toList());
        articleRevisionMapper.deleteBatchIds(stale);
    }

    // =================================================================
    // 定时发布
    // =================================================================

    /**
     * 把到期的定时草稿转为已发布。由 {@code ScheduledPublishJob} 每分钟调用一次。
     * <p>
     * 选择「定时任务改状态」而不是「查询时用 publish_at 过滤」，是为了不动
     * 列表 / 详情 / 归档 / 系列这一整条链路：发布状态一旦落库，所有既有查询自动正确。
     */
    @Transactional
    public int promoteScheduledArticles() {
        List<Article> due = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId)
                .eq(Article::getStatus, 0)
                .eq(Article::getIsDeleted, 0)
                .isNotNull(Article::getPublishAt)
                .le(Article::getPublishAt, LocalDateTime.now()));
        if (due.isEmpty()) {
            return 0;
        }
        for (Article a : due) {
            // 只翻状态，**不清 publish_at**：它就是这篇文章的发布时间，
            // 热力图与卡片日期都靠它（清掉会退回 create_time，等于定时发布的时间白选了）。
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, a.getId())
                    .set(Article::getStatus, 1));
            evictArticleCache(a.getId());
        }
        redisTemplate.delete(List.of(ArticleCacheKeys.ARCHIVE, ArticleCacheKeys.SERIES));
        return due.size();
    }

    // =================================================================
    // slug
    // =================================================================

    /** slug 是否可用（供编辑器实时预检）。回收站里的文章仍占着唯一索引，故一并计入占用。 */
    public boolean slugAvailable(String slug, Long excludeId) {
        if (StrUtil.isBlank(slug)) {
            return false;
        }
        return articleMapper.countBySlugIncludingDeleted(slug.trim(), excludeId) == 0;
    }

    private void ensureSlugAvailable(String slug, Long excludeId) {
        if (!slugAvailable(slug, excludeId)) {
            throw new BusinessException(400, "自定义 URL「" + slug + "」已被占用，请换一个");
        }
    }

    private String generateSlug(String title) {
        String slug = StrUtil.toUnderlineCase(title).replace('_', '-');
        if (StrUtil.isBlank(slug)) {
            slug = "post";
        }
        return slug + "-" + System.currentTimeMillis();
    }

    // =================================================================
    // 分类 / 标签
    // =================================================================

    public List<Category> listCategories() {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort));
    }

    public List<Tag> listTags() {
        List<Tag> tags = tagMapper.selectList(null);
        // 一条聚合 SQL 统计各标签下已发布文章数，
        // 替代原先「全量载入 article + article_tag 后在内存里分组」的做法
        Map<Long, Long> countMap = articleTagMapper.countPublishedByTag().stream()
                .filter(r -> r.getTagId() != null)
                .collect(Collectors.toMap(TagArticleCount::getTagId,
                        r -> r.getCnt() == null ? 0L : r.getCnt()));
        for (Tag tag : tags) {
            tag.setArticleCount(countMap.getOrDefault(tag.getId(), 0L).intValue());
        }
        return tags;
    }

    public List<Tag> getTagsByArticleId(Long articleId) {
        List<ArticleTag> ats = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (ats.isEmpty()) {
            return List.of();
        }
        List<Long> tagIds = ats.stream().map(ArticleTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds);
    }

    /**
     * 批量取多篇文章的标签名。
     *
     * <p>供 AI 工具检索结果使用：一次查回 5~8 篇的标签，避免逐篇调 {@link #getTagsByArticleId} 打出 N 次查询。
     */
    public Map<Long, List<String>> tagNamesByArticleIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        List<ArticleTag> relations = articleTagMapper.selectList(
                new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds));
        if (relations.isEmpty()) {
            return Map.of();
        }
        List<Long> tagIds = relations.stream().map(ArticleTag::getTagId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> nameById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        Map<Long, List<String>> result = new HashMap<>();
        for (ArticleTag relation : relations) {
            String name = nameById.get(relation.getTagId());
            if (name != null) {
                result.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>()).add(name);
            }
        }
        return result;
    }

    /** 标签全量替换：先删后插。标签数量很少，这样比逐条 diff 更不容易出错。 */
    private void syncTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>()
                .eq(ArticleTag::getArticleId, articleId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ArticleTag at = new ArticleTag();
            at.setArticleId(articleId);
            at.setTagId(tagId);
            articleTagMapper.insert(at);
        }
    }

    // =================================================================
    // 相邻文章 / 相关推荐
    // =================================================================

    /** 相关推荐条数上限 */
    private static final int RELATED_SIZE = 4;

    /**
     * 「相关推荐」候选查询的公共部分：列白名单 + 只取已发布未删除。
     * 列白名单是必须的 —— selectById / 无 select 的查询会把 content_md、content_html（LONGTEXT）
     * 一起拉出来，4 篇推荐白白带上几百 KB 的正文。
     */
    private LambdaQueryWrapper<Article> recommendBase() {
        return new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSlug, Article::getCover,
                        Article::getSummary, Article::getViewCount, Article::getCreateTime)
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0);
    }

    /**
     * 相关推荐。
     * <p>
     * 先取同分类的其它文章；**不足 4 篇时用最新文章补齐**。
     * 之前的实现只在 article.categoryId 非空且同分类下还有别的文章时才返回数据，
     * 于是「未分类」的文章、或分类里只有一篇的文章，文末的推荐区整块不显示 ——
     * 读者读到结尾就断了，没有任何下一步入口。
     */
    public List<Article> related(Long id) {
        Article article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getCategoryId)
                .eq(Article::getId, id));
        if (article == null) {
            return List.of();
        }

        List<Article> result = new ArrayList<>();
        if (article.getCategoryId() != null) {
            result.addAll(articleMapper.selectList(recommendBase()
                    .eq(Article::getCategoryId, article.getCategoryId())
                    .ne(Article::getId, id)
                    .orderByDesc(Article::getCreateTime)
                    .last("LIMIT " + RELATED_SIZE)));
        }

        if (result.size() < RELATED_SIZE) {
            List<Long> excludeIds = new ArrayList<>();
            excludeIds.add(id);
            for (Article a : result) {
                excludeIds.add(a.getId());
            }
            result.addAll(articleMapper.selectList(recommendBase()
                    .notIn(Article::getId, excludeIds)
                    .orderByDesc(Article::getCreateTime)
                    .last("LIMIT " + (RELATED_SIZE - result.size()))));
        }
        return result;
    }

    /**
     * 相邻文章。列表顺序为 is_top DESC, create_time DESC, id DESC（id 作为同秒创建的兜底排序）。
     * 用两次走索引的单行查询，替代前端原先「拉 100 篇全文到客户端再算下标」的做法（待办第 35 条）。
     */
    public Map<String, Object> neighbors(Long id) {
        Article current = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getIsTop, Article::getCreateTime)
                .eq(Article::getId, id));
        Map<String, Object> result = new HashMap<>();
        if (current == null || current.getCreateTime() == null) {
            return result;
        }
        int top = current.getIsTop() == null ? 0 : current.getIsTop();
        // 上一篇：排序中更靠前的一篇
        Article prev = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSlug)
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0)
                .and(w -> w.gt(Article::getIsTop, top)
                        .or(w2 -> w2.eq(Article::getIsTop, top).gt(Article::getCreateTime, current.getCreateTime()))
                        .or(w3 -> w3.eq(Article::getIsTop, top).eq(Article::getCreateTime, current.getCreateTime()).gt(Article::getId, id)))
                .orderByAsc(Article::getIsTop).orderByAsc(Article::getCreateTime).orderByAsc(Article::getId)
                .last("LIMIT 1"));
        // 下一篇：排序中更靠后的一篇
        Article next = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSlug)
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0)
                .and(w -> w.lt(Article::getIsTop, top)
                        .or(w2 -> w2.eq(Article::getIsTop, top).lt(Article::getCreateTime, current.getCreateTime()))
                        .or(w3 -> w3.eq(Article::getIsTop, top).eq(Article::getCreateTime, current.getCreateTime()).lt(Article::getId, id)))
                .orderByDesc(Article::getIsTop).orderByDesc(Article::getCreateTime).orderByDesc(Article::getId)
                .last("LIMIT 1"));
        result.put("prev", prev);
        result.put("next", next);
        return result;
    }

    // =================================================================
    // 导入 / 导出
    // =================================================================

    /**
     * 导入 .md。
     * <p>
     * 支持解析 front matter（title / slug / category / series / cover / summary / tags），
     * 因此「批量导出 → 再导入」可以形成闭环；老的无 front matter 文件行为不变
     * （标题取文件名，全文当正文）。
     */
    @Transactional
    public Article importMd(MultipartFile file, Long userId) throws IOException {
        String raw = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        FrontMatter.Parsed parsed = FrontMatter.parse(raw);
        Map<String, String> meta = parsed.meta();
        String title = meta.getOrDefault("title", stripExtension(file.getOriginalFilename()));

        Article article = new Article();
        article.setTitle(title);
        article.setContentMd(parsed.body());
        article.setSummary(meta.get("summary"));
        article.setCover(meta.get("cover"));
        article.setSeries(meta.get("series"));
        // front-matter 里没写分类（或写了库中不存在的分类）→ 归入「未建档文章」
        Long importedCategoryId = resolveCategoryByName(meta.get("category"));
        article.setCategoryId(importedCategoryId != null ? importedCategoryId : defaultCategoryId());
        article.setSlug(resolveImportedSlug(meta.get("slug"), title));
        article.setUserId(userId);
        article.setStatus(0);
        article.setIsTop(0);
        article.setWordCount(WordCounter.count(parsed.body()));
        articleMapper.insert(article);

        syncTags(article.getId(), resolveTagIdsByName(FrontMatter.splitList(meta.get("tags"))));
        adjustCategoryCount(article.getCategoryId(), 1);
        return article;
    }

    @Transactional
    public Article importWord(MultipartFile file, Long userId) throws IOException {
        String title = stripExtension(file.getOriginalFilename());
        try {
            String content = DocxToMdUtil.convert(file.getInputStream());
            Article article = new Article();
            article.setTitle(title);
            article.setContentMd(content);
            article.setSlug(resolveImportedSlug(null, title));
            article.setUserId(userId);
            article.setStatus(0);
            article.setIsTop(0);
            // Word 导入没有分类来源，同样归入「未建档文章」，别留 null
            article.setCategoryId(defaultCategoryId());
            article.setWordCount(WordCounter.count(content));
            articleMapper.insert(article);
            adjustCategoryCount(article.getCategoryId(), 1);
            return article;
        } catch (Exception e) {
            throw new BusinessException("Word 解析失败: " + e.getMessage());
        }
    }

    /** 导出为「front matter + 正文」，与 importMd 对称 */
    public String exportMarkdown(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }
        return exportMarkdown(article);
    }

    /** 批量导出：每篇之间以空行分隔，格式与单篇完全一致 */
    public String exportMarkdownBatch(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            Article article = articleMapper.selectById(id);
            if (article == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(exportMarkdown(article));
        }
        return sb.toString();
    }

    private String exportMarkdown(Article article) {
        Category category = article.getCategoryId() == null ? null : categoryMapper.selectById(article.getCategoryId());
        String tags = getTagsByArticleId(article.getId()).stream()
                .map(Tag::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("title", article.getTitle());
        meta.put("slug", article.getSlug());
        meta.put("date", article.getCreateTime() == null ? null : article.getCreateTime().format(FRONT_MATTER_TIME));
        meta.put("updated", article.getUpdateTime() == null ? null : article.getUpdateTime().format(FRONT_MATTER_TIME));
        meta.put("category", category == null ? null : category.getName());
        meta.put("series", article.getSeries());
        meta.put("tags", tags);
        meta.put("summary", article.getSummary());
        meta.put("cover", article.getCover());
        meta.put("status", article.getStatus() != null && article.getStatus() == 1 ? "published" : "draft");
        return FrontMatter.render(meta, article.getContentMd() == null ? "" : article.getContentMd());
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "未命名";
        }
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        // 文件名可能来自任意操作系统，去掉不能进 URL / 文件系统的字符
        return base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** 导入时优先用 front matter 里的 slug，冲突或为空则退回自动生成 */
    private String resolveImportedSlug(String slugFromMeta, String title) {
        if (StrUtil.isNotBlank(slugFromMeta) && slugAvailable(slugFromMeta.trim(), null)) {
            return slugFromMeta.trim();
        }
        String generated;
        do {
            generated = generateSlug(title);
        } while (!slugAvailable(generated, null));
        return generated;
    }

    private Long resolveCategoryByName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String target = name.trim();
        return categoryMapper.selectList(null).stream()
                .filter(c -> target.equalsIgnoreCase(c.getName() == null ? "" : c.getName().trim()))
                .map(Category::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * 取「未建档文章」分类的 id；不存在则自动创建。
     *
     * <p>为什么落到真实分类行、而不是只在展示层兜个名字：
     * 分类页的文章数是按 {@code category_id} 聚合的，只有真正写进库，
     * 「未建档文章」下面才能数得到文章，点进去也才能筛出东西。
     *
     * <p>并发安全：{@code category.slug} 上有唯一键，两个请求同时初始化时，
     * 后插入的那个会撞唯一键，此时回查一次即可拿到已存在的那条。
     */
    @Transactional
    public Long defaultCategoryId() {
        Category existing = findDefaultCategory();
        if (existing != null) {
            return existing.getId();
        }
        Category category = new Category();
        category.setName(CategoryConstants.DEFAULT_CATEGORY_NAME);
        category.setSlug(CategoryConstants.DEFAULT_CATEGORY_SLUG);
        category.setParentId(0L);
        category.setSort(CategoryConstants.DEFAULT_CATEGORY_SORT);
        category.setArticleCount(0);
        try {
            categoryMapper.insert(category);
            return category.getId();
        } catch (DuplicateKeyException e) {
            Category created = findDefaultCategory();
            if (created == null) {
                throw e;
            }
            return created.getId();
        }
    }

    /**
     * 级联删除分类时调用：把被删子树下的文章改挂到「未建档文章」并同步计数。
     * 直接改挂而不是置 NULL —— 分类页文章数按 category_id 聚合，置 NULL 要等下次启动才回填。
     */
    @Transactional
    public void cascadeReassignToDefault(List<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        Long defaultId = defaultCategoryId();
        int moved = articleMapper.reassignCategories(categoryIds, defaultId);
        if (moved > 0) {
            adjustCategoryCount(defaultId, moved);
        }
    }

    /** 先按固定 slug 找，再按名字找（兼容管理员手工建过同名分类的情况） */
    private Category findDefaultCategory() {
        Category bySlug = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getSlug, CategoryConstants.DEFAULT_CATEGORY_SLUG)
                .last("LIMIT 1"));
        if (bySlug != null) {
            return bySlug;
        }
        return categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getName, CategoryConstants.DEFAULT_CATEGORY_NAME)
                .last("LIMIT 1"));
    }

    /**
     * 把标签名解析成 id，不存在的自动创建。
     * 标签是轻量对象，导入时自动补建比「导入后逐个手加」友好得多；
     * 分类则相反（有层级与排序），所以只做匹配、不自动创建。
     */
    private List<Long> resolveTagIdsByName(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Map<String, Long> byName = new HashMap<>();
        for (Tag tag : tagMapper.selectList(null)) {
            if (tag.getName() != null) {
                byName.put(tag.getName().trim().toLowerCase(), tag.getId());
            }
        }
        List<Long> ids = new ArrayList<>();
        for (String name : names) {
            String key = name.trim().toLowerCase();
            Long id = byName.get(key);
            if (id == null) {
                Tag tag = new Tag();
                tag.setName(name.trim());
                tag.setSlug(uniqueTagSlug(name.trim()));
                tagMapper.insert(tag);
                id = tag.getId();
                byName.put(key, id);
            }
            ids.add(id);
        }
        return ids;
    }

    /** tag.slug 是 NOT NULL + 唯一索引，这里生成一个不冲突的值 */
    private String uniqueTagSlug(String name) {
        String base = name.length() > 90 ? name.substring(0, 90) : name;
        String candidate = base;
        int suffix = 2;
        while (isSlugTaken(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private boolean isSlugTaken(String slug) {
        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug));
        return count != null && count > 0;
    }

    // =================================================================
    // 内部工具
    // =================================================================

    /**
     * 落定发布状态与定时时间。
     * <p>
     * 规则：status=1 → 立即发布，**保留**用户选的发布时间（没选就取当前时间）；
     * status=0 且 publishAt 在未来 → 挂定时，到点由 ScheduledPublishJob 翻状态；
     * status=0 且 publishAt 为空或已过 → 纯草稿，并把 publishAt 清空。
     * <p>
     * ⚠️ 第三条曾经是「视同立即发布」，那是一个内容安全事故：
     * 编辑器默认把发布时间预填成「此刻」，提交时那一刻必然已经过去，
     * 于是「保存草稿」会把没写完的文章直接公开（2026-11-23 修）。
     * 清空 publishAt 同时掐掉第二个入口 —— ScheduledPublishJob 只扫 publish_at 非空的记录。
     * <p>
     * ⚠️ 这里曾经是「status=1 就把 publishAt 清成 NULL」。那会让「发布时间」这个字段
     * 对已发布文章永远为空，于是发布热力图 / 卡片日期只能退回 create_time ——
     * 用户明明选了时间却不生效。现在它的语义是「这篇文章的发布时间」，
     * 草稿阶段的未来时刻兼职「定时发布时刻」。
     */
    private void applyPublishSchedule(Article article, ArticleDTO dto, Integer originalStatus) {
        // 不传状态时的默认值要分新建和编辑：
        //   新建 → 草稿（0）
        //   编辑 → 保持原状态。若这里也默认 0，任何不传 status 的调用方
        //          （脚本、第三方、将来的新前端）编辑一篇已发布文章都会把它悄悄下架
        Integer status = dto.getStatus() != null
                ? dto.getStatus()
                : (originalStatus != null ? originalStatus : STATUS_DRAFT);
        LocalDateTime publishAt = dto.getPublishAt();

        // 「还在审核流程里」= 库里的原状态是待审或已驳回
        boolean inReview = originalStatus != null
                && (originalStatus == STATUS_PENDING || originalStatus == STATUS_REJECTED);

        if (status == STATUS_PUBLISHED) {
            // ⚠️ 待审/驳回稿的「上线」只能走 approve 接口，而那个接口是 hasRole('super')。
            // 但通用编辑器 PUT /article/{id} 只要 admin —— 普通管理员从后台列表点进
            // 「编辑」，在同一个页面点「发布」就能让一篇没过审的投稿直接公开，
            // 等于审核流程对他形同虚设。这里补上服务层这道闸（URL 层拦不住：
            // 同一个接口管理员本来就要能用来编辑已发布文章）。
            if (inReview && !isSuper()) {
                throw new BusinessException(403,
                        "这篇稿件还在审核流程里，只有超级管理员能发布。"
                                + "请让超管在「投稿审核」里处理，或你先保存草稿。");
            }
            article.setStatus(STATUS_PUBLISHED);
            article.setPublishAt(publishAt != null ? publishAt : LocalDateTime.now());
            return;
        }

        if (status == STATUS_PENDING || status == STATUS_REJECTED) {
            // ⚠️ 这里以前是「只要不是 1 就置 0」，于是每次自动保存都把待审稿打成草稿：
            // 管理员打开一篇投稿、什么都没改停 5 秒，稿子就从审核队列里消失了，
            // 作者那边看到的是「又变回草稿」，超管那边永远等不到它。
            // 现在原样保留审核态，并且不允许它带定时时间（审核态不能自动上线）。
            article.setStatus(status);
            article.setPublishAt(null);
            return;
        }

        article.setStatus(STATUS_DRAFT);
        if (publishAt == null) {
            article.setPublishAt(null);
        } else if (publishAt.isAfter(LocalDateTime.now())) {
            // 计划时刻在未来 —— 挂定时，别在这里改状态
            article.setPublishAt(publishAt);
        } else {
            // 过去的发布时间 + 草稿 = 用户只想存草稿。清空 publishAt 而不是"视同发布"。
            article.setPublishAt(null);
        }
    }

    /**
     * 事务提交之后再执行。
     *
     * <p>用在这里是因为「删向量」是不可逆的外部副作用：若先删了向量、数据库那边
     * 因为唯一键冲突之类回滚，文章还公开着、知识库里却没有它了 —— 读者问 AI 反而搜不到。
     * 等提交成功再删，最坏情况是文章下线了但向量还在，那由全量对账兜底。
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** 允许存空草稿，但不允许把空文章发出去 */
    private void requireContentWhenPublished(Article article) {        if (article.getStatus() != null && article.getStatus() == 1
                && StrUtil.isBlank(article.getContentMd())) {
            throw new BusinessException(400, "发布前请先填写正文；如果还没写完，可以先「保存草稿」");
        }
    }

    private void evictArticleCache(Long id) {
        try {
            redisTemplate.delete(ArticleCacheKeys.article(id));
        } catch (Exception ignored) {
            // 缓存清理失败等待 TTL 自然过期，不影响主流程
        }
    }

    private void adjustCategoryCount(Long categoryId, int delta) {
        if (categoryId == null) {
            return;
        }
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            return;
        }
        int current = category.getArticleCount() == null ? 0 : category.getArticleCount();
        category.setArticleCount(Math.max(0, current + delta));
        categoryMapper.updateById(category);
    }
}
