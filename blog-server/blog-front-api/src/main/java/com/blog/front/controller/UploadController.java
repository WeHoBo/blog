package com.blog.front.controller;

import com.blog.common.dto.Result;
import com.blog.common.file.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileStorage fileStorage;

    @PreAuthorize("hasRole('admin')")
    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        String url = fileStorage.upload(file);
        return Result.ok(url);
    }
}
