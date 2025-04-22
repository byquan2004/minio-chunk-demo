package com.quan.miniodemo.domain.dto;

import lombok.Data;

@Data
public class InitUploadDTO {
    private String fileName;
    private String fileMD5;
    private Integer chunkCount;
    private Long fileSize;
}

