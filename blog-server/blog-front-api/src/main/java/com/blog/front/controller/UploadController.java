package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.common.exception.BusinessException;
import com.blog.common.file.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    /** 允许上传的扩展名白名单（小写）。不包含 svg（可内嵌脚本，会造成存储型 XSS）。 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "avif", "pdf");

    private final FileStorage fileStorage;

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') != -1) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅允许: " + String.join(", ", ALLOWED_EXT));
        }
        // 二次校验：部分客户端会伪造 Content-Type，空类型或明显不符时拒绝
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")
                && !"application/pdf".equals(contentType)) {
            throw new BusinessException("文件内容类型不合法: " + contentType);
        }
        String url = fileStorage.upload(file);
        log.info("上传文件成功: ext={}, size={}B, url={}", ext, file.getSize(), url);
        return Result.ok(url);
    }
}
