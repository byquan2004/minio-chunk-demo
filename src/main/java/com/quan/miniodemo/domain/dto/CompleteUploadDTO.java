package com.quan.miniodemo.domain.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CompleteUploadDTO {
    private String fileName;

    private String fileMD5;

    private Integer chunkCount;
}