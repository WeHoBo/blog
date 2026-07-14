package com.blog.common.file;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Component
public class LocalFileStorage implements FileStorage {

    @Value("${file.upload.path:./uploads}")
    private String uploadPath;

    @Value("${file.upload.base-url:http://localhost:8080/uploads}")
    private String baseUrl;

    @Override
    public String upload(MultipartFile file) {
        try {
            String originalName = file.getOriginalFilename();
            String suffix = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf("."))
                    : ".png";
            String fileName = IdUtil.simpleUUID() + suffix;
            String dateDir = cn.hutool.core.date.DateUtil.today();
            File dir = new File(uploadPath, dateDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, fileName);
            FileUtil.writeFromStream(file.getInputStream(), dest);
            return baseUrl + "/" + dateDir + "/" + fileName;
        } catch (Exception e) {
            throw new com.blog.common.exception.BusinessException("文件上传失败: " + e.getMessage());
        }
    }
}
