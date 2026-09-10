package com.jobseeker.service;

import com.jobseeker.common.BizException;
import com.jobseeker.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 本服务自己的文件存储（简历、附件）。
 *
 * 与 Agent 的 AI 产物目录严格区分：本目录可写，Agent 目录只读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final StorageProperties props;

    public String save(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BizException("文件为空");
        }
        try {
            Path dir = Paths.get(props.getBaseDir(), subDir, LocalDate.now().toString());
            Files.createDirectories(dir);

            String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
            String safe = original.replaceAll("[\\\\/:*?\"<>|]", "_");
            String name = UUID.randomUUID().toString().substring(0, 8) + "-" + safe;

            Path target = dir.resolve(name);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return Paths.get(props.getBaseDir()).relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("保存文件失败", e);
            throw new BizException("保存文件失败：" + e.getMessage());
        }
    }

    /** 直接写入字节（用于从 Agent 产物导入，避免再经过 MultipartFile）。 */
    public String saveBytes(byte[] bytes, String subDir, String fileName) {
        try {
            Path dir = Paths.get(props.getBaseDir(), subDir, LocalDate.now().toString());
            Files.createDirectories(dir);
            String safe = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path target = dir.resolve(safe);
            Files.write(target, bytes);
            return Paths.get(props.getBaseDir()).relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("保存字节失败", e);
            throw new BizException("保存文件失败：" + e.getMessage());
        }
    }

    public Path resolve(String relativePath) {
        return Paths.get(props.getBaseDir(), relativePath);
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException e) {
            log.warn("删除文件失败：{}", e.getMessage());
        }
    }
}
