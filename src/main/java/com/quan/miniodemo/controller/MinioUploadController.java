package com.quan.miniodemo.controller;

import com.quan.miniodemo.domain.dto.CompleteUploadDTO;
import com.quan.miniodemo.domain.dto.InitUploadDTO;
import com.quan.miniodemo.domain.pojo.R;
import com.quan.miniodemo.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/minio/upload")
@Slf4j
public class MinioUploadController {
    
    @Autowired
    private MinioService minioService;
    
    @PostMapping("/init")
    public R<Map<String, Object>> initUpload(@RequestBody InitUploadDTO dto) {
        try {
            Map<String, Object> result = minioService.initUpload(dto);
            return R.ok(result);
        } catch (Exception e) {
            log.error("初始化上传失败", e);
            return R.error("初始化上传失败");
        }
    }
    
    @PostMapping("/chunk")
    public R<Void> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileMD5") String fileMD5,
            @RequestParam("chunkIndex") Integer chunkIndex) {
        try {
            minioService.uploadChunk(fileMD5, chunkIndex, file);
            return R.ok();
        } catch (Exception e) {
            log.error("上传分片失败", e);
            return R.error("上传分片失败");
        }
    }
    
    @PostMapping("/complete")
    public R<Map<String, Object>> completeUpload(@RequestBody CompleteUploadDTO dto) {
        try {
            String fileUrl = minioService.mergeChunks(
                    dto.getFileMD5(),
                    dto.getFileName(),
                    dto.getChunkCount()
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("url", fileUrl);
            return R.ok(result);
        } catch (Exception e) {
            log.error("完成上传失败", e);
            return R.error("完成上传失败");
        }
    }
}