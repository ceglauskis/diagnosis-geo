package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisQuery {

    private UUID id;
    private UUID diagnosisId;
    private String text;               // the full question
    private boolean isCustom;          // whether the user wrote it manually
    private LocalDateTime createdAt;
}
