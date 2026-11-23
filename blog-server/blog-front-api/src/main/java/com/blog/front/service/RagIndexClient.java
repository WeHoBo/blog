package com.blog.front.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 通知 RAG 知识库「这篇文章不该再被检索到」。
 *
 * <p><b>为什么需要</b>：文章下架（转草稿 / 进回收站 / 物理删除）只是把数据库里的
 * {@code status} 改掉了，向量库里那几百个块一个字都没动。于是检索照样能把它
 * 下架前的正文片段送给模型 —— 读者在文章页看不到的内容，问 AI 却问得出来。
 * 内容下线应该是整条链路一起下线。
 *
 * <p><b>为什么失败只记日志</b>：知识库是旁路，不能因为它挂了就让人存不了文章。
 * 真正的兜底是 {@code import_blog.py} 末尾的全量对账（{@code /prune-index}），
 * 它按「当前所有公开文章」的名单把多余的一律删掉。这里只是把窗口从「等下次对账」
 * 缩到「立刻」。
 *
 * <p>超时给 2 秒：这是在保存文章的请求链路里同步调用的，宁可放弃这次清理，
 * 也不能让"保存"卡在一个连不上的服务上。
 */
@Slf4j
@Component
public class RagIndexClient {

    @Value("${ai.rag-url:http://127.0.0.1:8000}")
    private String ragUrl;

    @Value("${ai.rag-token:}")
    private String ragToken;

    public void evictArticle(Long articleId) {
        if (articleId == null) {
            return;
        }
        evictArticles(List.of(articleId));
    }

    /**
     * 批量版本。批量删除是超管低频操作，但也没必要为十篇文章发十个请求。
     *
     * <p>⚠️ 注意它只做「删除」。把一篇已恢复的文章**重新加入**知识库需要正文，
     * 那要等下一次 {@code import_blog.py}（全量导入 + 对账），不在这里做。
     */
    public void evictArticles(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty() || StrUtil.isBlank(ragUrl)) {
            return;
        }
        try {
            HttpRequest req = HttpRequest.post(ragUrl + "/delete-articles")
                    .header("Content-Type", "application/json")
                    .body(JSON.toJSONString(Map.of("ids", articleIds)))
                    .timeout(2000);
            if (StrUtil.isNotBlank(ragToken)) {
                req.header("X-RAG-Token", ragToken);
            }
            req.execute();
        } catch (Exception e) {
            log.warn("[知识库] 文章 {} 的向量清理失败，等全量对账兜底: {}", articleIds, e.toString());
        }
    }
}
