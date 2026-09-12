package cn.hhu.sen.agentsproj.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ReportExportService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path exportDirectory;

    public ReportExportService(@Value("${app.export-dir:exports}") String exportDirectory) {
        this.exportDirectory = Path.of(exportDirectory);
    }

    public String export(String taskId,
                         String title,
                         String body,
                         List<String> tags,
                         String imageUrl) {
        try {
            Files.createDirectories(exportDirectory);
            String safeTaskId = sanitize(taskId);
            String filename = LocalDateTime.now().format(FILE_TIME) + "-" + safeTaskId + ".md";
            Path output = exportDirectory.resolve(filename).normalize();
            if (!output.getParent().equals(exportDirectory.toAbsolutePath().normalize())
                    && !output.getParent().equals(exportDirectory.normalize())) {
                throw new IllegalArgumentException("非法导出路径");
            }

            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(valueOrDefault(title, "GitPulse AI 报告")).append("\n\n");
            if (tags != null && !tags.isEmpty()) {
                markdown.append("标签：").append(String.join("、", tags)).append("\n\n");
            }
            if (imageUrl != null && !imageUrl.isBlank()) {
                markdown.append("图片：").append(imageUrl).append("\n\n");
            }
            markdown.append(valueOrDefault(body, ""));
            Files.writeString(output, markdown.toString(), StandardCharsets.UTF_8);
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("报告导出失败: " + e.getMessage(), e);
        }
    }

    private String sanitize(String value) {
        String safe = value == null ? "task" : value.replaceAll("[^A-Za-z0-9_-]", "");
        return safe.isBlank() ? "task" : safe;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
