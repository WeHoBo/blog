package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.ArticleDTO;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
public class ArticleService {

    /** 单篇文章最多保留的历史版本数，超出即淘汰最旧的 */
    private static final int MAX_REVISIONS = 30;

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

    // =================================================================
    // 列表 / 详情
    // =================================================================

    public Page<Article> page(int pageNum, int pageSize, Long categoryId, Long tagId, String keyword, List<Long> categoryIds, String sort) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0);
        if (categoryIds != null && !categoryIds.isEmpty()) {
            wrapper.in(Article::getCategoryId, categoryIds);
        } else if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.like(Article::getTitle, keyword);
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
                Article::getCategoryId, Article::getCreateTime, Article::getWordCount,
                Article::getUserId);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
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
                Article::getCategoryId, Article::getSeries, Article::getIsTop, Article::getViewCount,
                Article::getCommentCount, Article::getLikeCount, Article::getWordCount,
                Article::getIsDeleted,
                Article::getCreateTime, Article::getUpdateTime);
        return articleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public Article getPublicById(Long id) {
        String key = ArticleCacheKeys.article(id);
        // 本次缓存窗口内的浏览量增量（与文章缓存同 TTL，过期即重置）
        String viewKey = ArticleCacheKeys.viewCount(id);
        Article article = (Article) redisTemplate.opsForValue().get(key);
        if (article == null) {
            article = articleMapper.selectById(id);
            if (article == null || !isPublicArticle(article)) {
                throw new BusinessException(404, "文章不存在");
            }
            redisTemplate.opsForValue().set(key, article, ArticleCacheKeys.ARTICLE_TTL);
            redisTemplate.opsForValue().set(viewKey, 0, ArticleCacheKeys.ARTICLE_TTL);
        } else if (!isPublicArticle(article)) {
            redisTemplate.delete(key);
            throw new BusinessException(404, "文章不存在");
        }
        // 持久化：单条主键 UPDATE 原子自增（数据库始终是浏览量的真实来源）
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .setSql("view_count = IFNULL(view_count, 0) + 1"));
        // 展示：用 Redis 计数器自增，免去每次访问都回查一次数据库
        // 展示值 = 缓存加载时的 DB 基准值 + 窗口内增量，与数据库保持同步
        Long delta = redisTemplate.opsForValue().increment(viewKey);
        article.setViewCount((article.getViewCount() == null ? 0 : article.getViewCount())
                + (delta == null ? 1 : delta.intValue()));
        return article;
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
        article.setCategoryId(dto.getCategoryId());
        article.setSeries(dto.getSeries());
        article.setIsTop(dto.getIsTop() != null ? dto.getIsTop() : 0);
        article.setWordCount(WordCounter.count(dto.getContentMd()));
        article.setUserId(userId);
        applyPublishSchedule(article, dto);
        requireContentWhenPublished(article);

        try {
            articleMapper.insert(article);
        } catch (DuplicateKeyException e) {
            // 并发下的兜底：两个请求同时通过了上面的检查
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }

        syncTags(article.getId(), dto.getTagIds());
        adjustCategoryCount(dto.getCategoryId(), 1);
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
        article.setCategoryId(dto.getCategoryId());
        article.setSeries(dto.getSeries());
        article.setWordCount(WordCounter.count(nextContent));
        if (dto.getIsTop() != null) {
            article.setIsTop(dto.getIsTop());
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

        applyPublishSchedule(article, dto);
        requireContentWhenPublished(article);

        try {
            articleMapper.updateById(article);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(400, "别名已被占用，请换一个自定义 URL 后重试");
        }

        syncTags(id, dto.getTagIds());
        if (!Objects.equals(oldCategoryId, dto.getCategoryId())) {
            adjustCategoryCount(oldCategoryId, -1);
            adjustCategoryCount(dto.getCategoryId(), 1);
        }
        evictArticleCache(id);
        return article;
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
        article.setCategoryId(revision.getCategoryId());
        article.setSeries(revision.getSeries());
        article.setWordCount(WordCounter.count(revision.getContentMd()));
        articleMapper.updateById(article);

        if (!Objects.equals(oldCategoryId, revision.getCategoryId())) {
            adjustCategoryCount(oldCategoryId, -1);
            adjustCategoryCount(revision.getCategoryId(), 1);
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
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, a.getId())
                    .set(Article::getStatus, 1)
                    .set(Article::getPublishAt, null));
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

    public List<Article> related(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null || article.getCategoryId() == null) {
            return List.of();
        }
        // 相关推荐只渲染标题/封面/时间，不返回正文，避免响应体里塞进 4 篇完整文章
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSlug, Article::getCover, Article::getCreateTime)
                .eq(Article::getCategoryId, article.getCategoryId())
                .eq(Article::getStatus, 1)
                .eq(Article::getIsDeleted, 0)
                .ne(Article::getId, id)
                .orderByDesc(Article::getCreateTime)
                .last("LIMIT 4"));
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
        article.setCategoryId(resolveCategoryByName(meta.get("category")));
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
            article.setWordCount(WordCounter.count(content));
            articleMapper.insert(article);
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
     * 规则：status=1 → 立即发布并清掉 publishAt；status=0 且 publishAt 在未来 → 挂定时；
     * status=0 且 publishAt 已过 → 视同立即发布（用户意图明显是「现在就要发」）。
     */
    private void applyPublishSchedule(Article article, ArticleDTO dto) {
        Integer status = dto.getStatus() == null ? 0 : dto.getStatus();
        LocalDateTime publishAt = dto.getPublishAt();
        if (status == 1) {
            article.setStatus(1);
            article.setPublishAt(null);
            return;
        }
        article.setStatus(0);
        if (publishAt == null) {
            article.setPublishAt(null);
        } else if (publishAt.isAfter(LocalDateTime.now())) {
            article.setPublishAt(publishAt);
        } else {
            article.setStatus(1);
            article.setPublishAt(null);
        }
    }

    /** 允许存空草稿，但不允许把空文章发出去 */
    private void requireContentWhenPublished(Article article) {
        if (article.getStatus() != null && article.getStatus() == 1
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
