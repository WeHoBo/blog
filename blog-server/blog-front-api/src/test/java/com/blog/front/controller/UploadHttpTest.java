package com.blog.front.controller;

import com.blog.common.entity.OperationLog;
import com.blog.common.exception.GlobalExceptionHandler;
import com.blog.common.file.LocalFileStorage;
import com.blog.common.mapper.OperationLogMapper;
import com.blog.common.mapper.UploadFileMapper;
import com.blog.front.config.ApiAccessLogFilter;
import com.blog.front.config.WebMvcConfig;
import com.blog.front.service.OperationLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.time.Duration;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadHttpTest {
    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({UploadController.class, LocalFileStorage.class, GlobalExceptionHandler.class, WebMvcConfig.class})
    static class WebConfig {
        @Bean UploadFileMapper uploadFileMapper() { return mock(UploadFileMapper.class); }
        @Bean StandardServletMultipartResolver multipartResolver() { return new StandardServletMultipartResolver(); }
    }

    @Test
    void uploadFormatAndParserFailuresReachAuditWithTheResponseId(@TempDir Path temp) throws Exception {
        var audit = mock(OperationLogMapper.class);
        var factory = new TomcatServletWebServerFactory(0);
        factory.setAddress(InetAddress.getByName("127.0.0.1"));
        factory.setBaseDirectory(temp.toFile());
        var files = Files.createDirectory(temp.resolve("stored"));
        try (var context = new AnnotationConfigWebApplicationContext()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=8KB",
                    "file.upload.path=" + files.toAbsolutePath().toString().replace('\\', '/'),
                    "file.upload.base-url=/uploads");
            context.register(WebConfig.class);
            var server = factory.getWebServer(sc -> {
                var servlet = sc.addServlet("dispatcher", new DispatcherServlet(context));
                servlet.setMultipartConfig(new MultipartConfigElement(temp.toString(), 1024, 8192, 0));
                servlet.addMapping("/");
                var access = new ApiAccessLogFilter(new OperationLogService(audit));
                Filter authenticated = (request, response, chain) -> {
                    request.setAttribute(ApiAccessLogFilter.UID_ATTR, 7L);
                    access.doFilter(request, response, chain);
                };
                sc.addFilter("access", authenticated).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
            });
            var accessLogger = (Logger) LoggerFactory.getLogger("ACCESS");
            var events = new ListAppender<ILoggingEvent>() {
                @Override protected void append(ILoggingEvent event) {
                    event.prepareForDeferredProcessing();
                    super.append(event);
                }
            };
            events.start();
            accessLogger.addAppender(events);
            try {
                server.start();
                URI endpoint = URI.create("http://127.0.0.1:" + server.getPort() + "/api/upload");
                var png = image("png", 1);
                var small = send(endpoint, "cover.png", "image/png", png);
                var unsupported = send(endpoint, "cover.tiff", "image/tiff", image("tiff", 1));
                var large = send(endpoint, "cover.png", "image/png", image("png", 32));
                assertEquals(List.of(200, 415, 413), List.of(small.statusCode(), unsupported.statusCode(), large.statusCode()));
                var json = new ObjectMapper();
                assertEquals(415, json.readTree(unsupported.body()).get("code").asInt());
                assertTrue(json.readTree(large.body()).get("message").asText().contains("1KB"));
                var saved = endpoint.resolve(json.readTree(small.body()).get("data").asText());
                var download = HttpClient.newHttpClient().send(HttpRequest.newBuilder(saved).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                assertEquals(200, download.statusCode());
                assertArrayEquals(png, download.body());
                var rows = ArgumentCaptor.forClass(OperationLog.class);
                verify(audit, timeout(2000).times(3)).insert(rows.capture());
                assertEquals(List.of(1, 0, 0), rows.getAllValues().stream().map(OperationLog::getStatus).toList());
                var requestIds = List.of(small, unsupported, large).stream()
                        .map(r -> r.headers().firstValue("X-Request-Id").orElseThrow()).toList();
                assertEquals(requestIds, rows.getAllValues().stream().map(OperationLog::getRequestId).toList());
                assertEquals(requestIds, events.list.stream().map(event -> event.getMDCPropertyMap().get("rid")).toList());
            } finally {
                accessLogger.detachAppender(events);
                events.stop();
                server.stop();
            }
        }
    }

    private byte[] image(String format, int width) throws IOException {
        var pixels = new BufferedImage(width, width, BufferedImage.TYPE_INT_RGB);
        var random = new Random(17);
        for (int y = 0; y < width; y++) {
            for (int x = 0; x < width; x++) pixels.setRGB(x, y, random.nextInt());
        }
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(pixels, format, bytes));
        return bytes.toByteArray();
    }

    private HttpResponse<String> send(URI endpoint, String filename, String mime, byte[] file) throws Exception {
        String boundary = "upload-test-boundary";
        String prefix = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" +
                filename + "\"\r\nContent-Type: " + mime + "\r\n\r\n";
        var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofString(prefix),
                HttpRequest.BodyPublishers.ofByteArray(file), HttpRequest.BodyPublishers.ofString("\r\n--" + boundary + "--\r\n"));
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
