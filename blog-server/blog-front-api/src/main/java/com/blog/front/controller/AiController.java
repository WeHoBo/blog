package com.blog.front.controller;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.blog.common.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * AI 问答代理：转发前端请求到本地 RAG 服务（Python，127.0.0.1:8000）。
 * /chat     RAG 检索问答（流式，响应尾带 @@SOURCES@@）
 * /free     自由问答（代码解释/全文总结，无检索，流式）
 * 鉴权由 SecurityConfig 的 /api/** authenticated 统一控制（需登录）。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    @Value("${ai.rag-url:http://127.0.0.1:8000}")
    private String ragUrl;

    private StreamingResponseBody forward(String path, String rawBody) {
        final String payload = (rawBody == null || rawBody.isBlank()) ? "{}" : rawBody;
        return outputStream -> {
            HttpResponse resp = HttpRequest.post(ragUrl + path)
                    .header("Content-Type", "application/json")
                    .body(payload.getBytes(StandardCharsets.UTF_8))
                    .timeout(180000)
                    .execute();
            try (InputStream in = resp.bodyStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    outputStream.write(buf, 0, n);
                }
                outputStream.flush();
            } catch (Exception e) {
                // 转发中断（客户端断开）不视为服务端错误
                return;
            }
        };
    }

    /** RAG 流式问答（检索博客文章后回答） */
    @PostMapping("/chat")
    public StreamingResponseBody chat(@RequestBody(required = false) String rawBody,
                                      jakarta.servlet.http.HttpServletResponse httpResponse) {
        httpResponse.setContentType("text/plain;charset=utf-8");
        return forward("/chat", rawBody);
    }

    /** 自由问答（不检索：代码解释 / 全文总结等） */
    @PostMapping("/free")
    public StreamingResponseBody free(@RequestBody(required = false) String rawBody,
                                      jakarta.servlet.http.HttpServletResponse httpResponse) {
        httpResponse.setContentType("text/plain;charset=utf-8");
        return forward("/free-chat", rawBody);
    }

    /** 获取 RAG 已入库文档列表（调试用） */
    @GetMapping("/documents")
    public Result<?> documents() {
        try {
            HttpResponse resp = HttpRequest.get(ragUrl + "/documents")
                    .timeout(10000)
                    .execute();
            return Result.ok(com.alibaba.fastjson2.JSON.parse(resp.body()));
        } catch (Exception e) {
            return Result.fail(500, "RAG 服务不可用: " + e.getMessage());
        }
    }
}
