package com.blog.front.controller;

import com.blog.front.service.AiModelService;
import com.blog.front.service.AiQuotaService;
import com.blog.front.service.AiUsageService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AiStreamTest {
    @Test
    void forwardsFirstEventWhileUpstreamIsStillProducing() throws Exception {
        CountDownLatch firstDelivered = new CountDownLatch(1);
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat", request -> {
            try (request) {
                request.getResponseHeaders().set("Content-Type", "text/event-stream");
                request.sendResponseHeaders(200, 0);
                var out = request.getResponseBody();
                out.write("data: first\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                if (firstDelivered.await(5, TimeUnit.SECONDS)) {
                    out.write("data: last\n\n".getBytes(StandardCharsets.UTF_8));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        upstream.start();
        try {
            var quota = mock(AiQuotaService.class);
            when(quota.check(isNull(), eq(false), anyString()))
                    .thenReturn(new AiQuotaService.Decision(true, "", 0, 0, 0));
            var controller = new AiController(quota, mock(AiUsageService.class), mock(AiModelService.class));
            ReflectionTestUtils.setField(controller, "modelCode", "test");
            ReflectionTestUtils.setField(controller, "ragUrl", "http://127.0.0.1:" + upstream.getAddress().getPort());
            var response = new ByteArrayOutputStream() {
                @Override
                public void flush() {
                    if (toString(StandardCharsets.UTF_8).contains("data: first\n\n")) firstDelivered.countDown();
                }
            };
            controller.chat("{}", new MockHttpServletRequest(), new MockHttpServletResponse()).writeTo(response);
            assertEquals("data: first\n\ndata: last\n\n", response.toString(StandardCharsets.UTF_8));
            verify(quota).release(null);
        } finally {
            upstream.stop(0);
        }
    }
}
