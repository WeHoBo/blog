package com.blog.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 上传文件元信息，用于前端「素材库」复用历史图片。
 * 文件本体仍由 FileStorage 负责，这里只留一份可检索的记录。
 */
@Data
@TableName("upload_file")
public class UploadFile implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 访问地址（FileStorage 返回值） */
    private String url;

    private String originalName;

    /** 小写扩展名，便于前端按类型筛选 */
    private String ext;

    private String contentType;

    private Long fileSize;

    private Long uploaderId;

    private LocalDateTime createTime;
}
