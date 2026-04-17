package com.aigrowth.agent_backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisStartResponse {

    private String diagnosisId;
    private String status;           // PENDING, PROCESSING, etc.
    private String message;
    private LocalDateTime createdAt;
}