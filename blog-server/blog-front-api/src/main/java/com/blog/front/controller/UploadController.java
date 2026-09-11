package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.UploadFile;
import com.blog.common.exception.BusinessException;
import com.blog.common.file.FileStorage;
import com.blog.common.mapper.UploadFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UploadFileMapper uploadFileMapper;

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
        // 记一笔素材库流水。记录失败不影响本次上传结果 —— 图已经存好了，
        // 不能因为一条统计记录把用户的图弄丢。
        try {
            UploadFile record = new UploadFile();
            record.setUrl(url);
            record.setOriginalName(originalName);
            record.setExt(ext);
            record.setContentType(contentType);
            record.setFileSize(file.getSize());
            record.setUploaderId(currentUserId());
            uploadFileMapper.insert(record);
        } catch (Exception e) {
            log.warn("上传记录写入失败（不影响文件本身）: {}", e.getMessage());
        }
        log.info("上传文件成功: ext={}, size={}B, url={}", ext, file.getSize(), url);
        return Result.ok(url);
    }

    /** 素材库分页：按扩展名 / 文件名筛选，最新在前 */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/files")
    public Result<Page<UploadFile>> files(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "24") int pageSize,
            @RequestParam(required = false) String ext,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<UploadFile> wrapper = new LambdaQueryWrapper<>();
        if (ext != null && !ext.isBlank()) {
            wrapper.eq(UploadFile::getExt, ext.toLowerCase(Locale.ROOT).trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(UploadFile::getOriginalName, keyword.trim());
        }
        wrapper.orderByDesc(UploadFile::getCreateTime).orderByDesc(UploadFile::getId);
        int size = Math.min(Math.max(1, pageSize), 100);
        return Result.ok(uploadFileMapper.selectPage(new Page<>(Math.max(1, pageNum), size), wrapper));
    }

    /** 素材库「图片」筛选时用，避免前端硬编码后缀列表 */
    @PreAuthorize("hasRole('admin')")
    @GetMapping("/exts")
    public Result<Set<String>> exts() {
        return Result.ok(ALLOWED_EXT);
    }

    private Long currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long id)) {
            return null;
        }
        return id;
    }
}
