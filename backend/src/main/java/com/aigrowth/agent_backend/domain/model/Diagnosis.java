package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Diagnosis {

    private UUID id;
    private UUID userId;
    private UUID clientBrandId;
    private String language;           // "pt", "en", "es"
    private DiagnosisStatus status;    // PENDING, PROCESSING, COMPLETED, FAILED
    private LocalDateTime requestedAt;
    private LocalDateTime completedAt;
}