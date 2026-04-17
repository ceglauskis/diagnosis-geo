package com.aigrowth.agent_backend.infrastructure.store;

import com.aigrowth.agent_backend.application.dto.DiagnosisResultResponse;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store to keep diagnosis context between steps.
 * Temporary substitute until database integration.
 */
@Component
public class DiagnosisContextStore {

    private final Map<String, SiteContent> store = new ConcurrentHashMap<>();
    private final Map<String, String> emailStore = new ConcurrentHashMap<>();
    private final Map<String, DiagnosisResultResponse> resultStore = new ConcurrentHashMap<>();

    public void save(String diagnosisId, SiteContent content) {
        store.put(diagnosisId, content);
    }

    public SiteContent get(String diagnosisId) {
        return store.get(diagnosisId);
    }

    public boolean exists(String diagnosisId) {
        return store.containsKey(diagnosisId);
    }

    public void saveEmail(String diagnosisId, String email) {
        emailStore.put(diagnosisId, email);
    }

    public String getEmail(String diagnosisId) {
        return emailStore.get(diagnosisId);
    }

    public void saveResult(String diagnosisId, DiagnosisResultResponse result) {
        resultStore.put(diagnosisId, result);
    }

    public DiagnosisResultResponse getResult(String diagnosisId) {
        return resultStore.get(diagnosisId);
    }
}
