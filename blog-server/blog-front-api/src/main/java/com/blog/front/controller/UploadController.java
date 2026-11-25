package com.blog.front.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.dto.Result;
import com.blog.common.entity.UploadFile;
import com.blog.common.file.FileStorage;
import com.blog.common.mapper.UploadFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

    /** 头像只允许图片，且不要 pdf/ico —— 头像框是固定尺寸的方形，矢量/文档类型没有意义。 */
    private static final Set<String> AVATAR_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif");

    /** 头像大小上限 2MB。前端会先压到 512px 的 WebP，正常只有几十 KB。 */
    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

    private final FileStorage fileStorage;
    private final UploadFileMapper uploadFileMapper;

    /**
     * 上传**自己的头像**。
     *
     * 为什么单独开一个接口，而不是把 `/api/upload` 的 `hasRole('admin')` 拆掉：
     * `/api/upload` 是「后台素材库」的入口，它会把每次上传写进 `upload_file` 表，
     * 素材库页面按这张表分页展示。如果直接放开给所有登录用户，普通用户的头像就会混进
     * 管理员的素材库列表里，越用越乱。
     * 所以这里：任何登录用户可用、只收图片、限 2MB、**不写素材库表**。
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件为空");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "头像不能超过 2 MiB");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') != -1) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!AVATAR_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "头像仅支持 " + String.join(", ", AVATAR_EXT));
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !contentType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "请选择受支持的图片文件");
        }
        String url = fileStorage.upload(file);
        log.info("头像上传成功: userId={}, ext={}, size={}B, url={}",
                currentUserId(), ext, file.getSize(), url);
        return Result.ok(url);
    }

    /**
     * 上传图片 / 附件（素材库入口）。
     *
     * <p>⚠️ 这里放行 {@code author} 是有意的：作者在自己的写作后台里要传封面和正文插图，
     * 而编辑器用的就是这一个接口。不放行的话作者一传图就 403，整套投稿功能没法用。
     *
     * <p>代价是作者传的图也会写进 {@code upload_file} 表，会出现在超管的素材库里 ——
     * 这是可接受的（那张表本来就是"站内上传过的文件"清单），
     * 比单独再开一个不写表的接口要简单得多。
     */
    @PreAuthorize("hasAnyRole('admin','author')")
    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.lastIndexOf('.') != -1) {
            ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型，仅允许 " + String.join(", ", ALLOWED_EXT));
        }
        // 二次校验：部分客户端会伪造 Content-Type，空类型或明显不符时拒绝
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !("pdf".equals(ext) ? "application/pdf".equals(contentType) : contentType.startsWith("image/"))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "文件类型与扩展名不符");
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
