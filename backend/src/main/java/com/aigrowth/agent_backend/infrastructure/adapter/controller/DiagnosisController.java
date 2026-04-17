package com.aigrowth.agent_backend.infrastructure.adapter.controller;

import com.aigrowth.agent_backend.application.dto.*;
import com.aigrowth.agent_backend.application.usecase.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/diagnosis")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DiagnosisController {

    private final StartDiagnosisUseCase startDiagnosisUseCase;
    private final GenerateQueriesUseCase generateQueriesUseCase;
    private final AnalyzeWithGeminiUseCase analyzeWithGeminiUseCase;
    private final com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore contextStore;
    private final com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger diagnosisLogger;

    // ==================== STEP 1 - Start Diagnosis ====================
    @PostMapping("/start")
    public ResponseEntity<DiagnosisStartResponse> startDiagnosis(
            @Valid @RequestBody DiagnosisStartRequest request) {

        DiagnosisStartResponse response = startDiagnosisUseCase.execute(request);
        return ResponseEntity.ok(response);
    }

    // ==================== STEP 2 - Generate Queries ====================
    @GetMapping("/{diagnosisId}/queries")
    public ResponseEntity<List<QueryResponse>> getQueries(
            @PathVariable String diagnosisId) {

        List<QueryResponse> queries = generateQueriesUseCase.execute(diagnosisId);
        return ResponseEntity.ok(queries);
    }

    // ==================== STEP 3 - Analyze with Gemini ====================
    @PostMapping("/analyze")
    public ResponseEntity<DiagnosisResultResponse> analyze(
            @Valid @RequestBody AnalyzeRequest request) {

        DiagnosisResultResponse result = analyzeWithGeminiUseCase.execute(request);
        return ResponseEntity.ok(result);
    }

    // ==================== STEP 4 - Save Lead Email ====================
    @PostMapping("/email")
    public ResponseEntity<Map<String, String>> saveEmail(
            @Valid @RequestBody SaveEmailRequest request) {

        contextStore.saveEmail(request.getDiagnosisId(), request.getEmail());
        diagnosisLogger.logEmail(request.getDiagnosisId(), request.getEmail());

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "Email saved successfully"
        ));
    }

    // ==================== Retrieve result (for page reload) ====================
    @GetMapping("/{diagnosisId}")
    public ResponseEntity<DiagnosisResultResponse> getDiagnosis(
            @PathVariable String diagnosisId) {

        DiagnosisResultResponse result = contextStore.getResult(diagnosisId);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result);
    }
}