package cn.edu.ai.agent.service.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文档解析器
 * 使用 Apache Tika 解析多种文档格式，提取纯文本内容
 * 支持 PDF、DOCX、TXT、HTML、Markdown 等
 */
@Slf4j
@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    /**
     * 解析上传文件，提取文本内容
     *
     * @param file 上传的文件
     * @return 提取的文本内容
     */
    public String parse(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("开始解析文档: fileName={}, contentType={}", fileName, file.getContentType());

        try (InputStream inputStream = file.getInputStream()) {
            tika.setMaxStringLength(10 * 1024 * 1024);
            // 自动检测文件类型（Word、PDF、Excel、TXT、HTML 等），并将文件内容解析成纯文本字符串
            String content = tika.parseToString(inputStream);

            content = cleanText(content);

            log.info("文档解析完成: fileName={}, textLength={}", fileName, content.length());
            return content;
        } catch (Exception e) {
            log.error("文档解析失败: fileName={}", fileName, e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清洗文本：去除多余空白、特殊字符
     */
    private String cleanText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("\n{3,}", "\n\n")   // 将 3 个或以上的连续换行符压缩为 2 个
                .replaceAll("[ \\t]{2,}", " ")  // 将 2 个或以上的连续空格或制表符压缩为 1 个普通空格
                .trim();
    }
}
