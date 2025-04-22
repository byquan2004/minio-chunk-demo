package com.quan.miniodemo.service;

import com.quan.miniodemo.config.MinioConfig;
import com.quan.miniodemo.domain.dto.InitUploadDTO;
import io.minio.*;
import io.minio.messages.Item;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class MinioService {
    @Autowired
    private MinioConfig minioConfig;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        try {
            minioClient = MinioClient.builder()
                    .endpoint(minioConfig.getEndpoint())
                    .credentials(minioConfig.getAccessKey(), minioConfig.getSecretKey())
                    .build();

            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .build());
            }
        } catch (Exception e) {
            log.error("初始化 MinIO 客户端失败", e);
            throw new RuntimeException("初始化 MinIO 客户端失败", e);
        }
    }

    public void uploadChunk(String fileMD5, int chunkIndex, MultipartFile file) {
        try {
            String chunkKey = String.format("chunks/%s/%d", fileMD5, chunkIndex);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(chunkKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .build()
            );
        } catch (Exception e) {
            log.error("上传分片失败", e);
            throw new RuntimeException("上传分片失败", e);
        }
    }

    public String mergeChunks(String fileMD5, String fileName, int chunkCount) {
        try {
            List<ComposeSource> sources = new ArrayList<>();

            // 验证所有分片并准备合并
            for (int i = 0; i < chunkCount; i++) {
                String chunkKey = String.format("chunks/%s/%d", fileMD5, i);
                sources.add(
                        ComposeSource.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(chunkKey)
                                .build()
                );
            }

            // 合并文件
            String finalKey = "files/" + fileMD5 + "/" + fileName;
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(finalKey)
                            .sources(sources)
                            .build()
            );

            // 异步清理分片
            CompletableFuture.runAsync(() -> {
                try {
                    for (int i = 0; i < chunkCount; i++) {
                        String chunkKey = String.format("chunks/%s/%d", fileMD5, i);
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(minioConfig.getBucketName())
                                        .object(chunkKey)
                                        .build()
                        );
                    }
                } catch (Exception e) {
                    log.error("清理分片失败", e);
                }
            });

            return finalKey;
        } catch (Exception e) {
            log.error("合并分片失败", e);
            throw new RuntimeException("合并分片失败: " + e.getMessage());
        }
    }

    // 添加 initUpload 方法
    public Map<String, Object> initUpload(InitUploadDTO dto) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查文件是否已存在
            String finalKey = "files/" + dto.getFileMD5() + "/" + dto.getFileName();
            try {
                minioClient.statObject(
                        StatObjectArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .object(finalKey)
                                .build()
                );
                // 文件已存在
                result.put("status", "COMPLETED");
                result.put("url", finalKey);
                return result;
            } catch (Exception e) {
                // 文件不存在，继续检查分片
            }

            // 检查已上传的分片
            Set<Integer> uploadedChunks = new HashSet<>();
            String chunkPrefix = "chunks/" + dto.getFileMD5() + "/";

            try {
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(minioConfig.getBucketName())
                                .prefix(chunkPrefix)
                                .recursive(true)
                                .build()
                );

                for (Result<Item> item : results) {
                    String chunkName = item.get().objectName();
                    String indexStr = chunkName.substring(chunkPrefix.length());
                    try {
                        uploadedChunks.add(Integer.parseInt(indexStr));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid chunk name: {}", chunkName);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to list chunks", e);
            }

            result.put("status", "UPLOADING");
            result.put("uploadedChunks", new ArrayList<>(uploadedChunks));
            return result;

        } catch (Exception e) {
            log.error("初始化上传失败", e);
            throw new RuntimeException("初始化上传失败", e);
        }
    }
}