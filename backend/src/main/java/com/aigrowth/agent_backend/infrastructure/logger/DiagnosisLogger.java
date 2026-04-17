package com.aigrowth.agent_backend.infrastructure.logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class DiagnosisLogger {

    private final ObjectMapper objectMapper;
    private final String baseLogDir;

    public DiagnosisLogger(@Value("${diagnosis.logs.dir:logs/diagnosis}") String baseLogDir) {
        this.baseLogDir = baseLogDir;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void logScrape(String diagnosisId, Object request, Object siteContent) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", "1_SCRAPE");
        entry.put("diagnosisId", diagnosisId);
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("input", request);
        entry.put("output", siteContent);
        save(diagnosisId, "step1_scrape.json", entry);
    }

    public void logQueriesGenerated(String diagnosisId, String prompt, Object queries) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", "2_GENERATE_QUERIES");
        entry.put("diagnosisId", diagnosisId);
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("promptSentToGemini", prompt);
        entry.put("output", queries);
        save(diagnosisId, "step2_queries.json", entry);
    }

    public void logAnalysis(String diagnosisId, Object request, String prompt,
                            String rawGeminiResponse, Object result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", "3_ANALYZE");
        entry.put("diagnosisId", diagnosisId);
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("input", request);
        entry.put("promptSentToGemini", prompt);
        entry.put("rawGeminiResponse", rawGeminiResponse);
        entry.put("output", result);
        save(diagnosisId, "step3_analysis.json", entry);
    }

    public void logEmail(String diagnosisId, String email) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", "4_EMAIL_CAPTURED");
        entry.put("diagnosisId", diagnosisId);
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("email", email);
        save(diagnosisId, "step4_email.json", entry);
    }

    private void save(String diagnosisId, String filename, Object data) {
        try {
            Path dir = Paths.get(baseLogDir, diagnosisId);
            Files.createDirectories(dir);
            Path file = dir.resolve(filename);
            objectMapper.writeValue(file.toFile(), data);
            log.info("[DiagnosisLogger] Saved: {}", file.toAbsolutePath());
        } catch (IOException e) {
            log.warn("[DiagnosisLogger] Failed to save {}/{}: {}", diagnosisId, filename, e.getMessage());
        }
    }
}
