package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {

    private UUID id;
    private UUID diagnosisId;
    private String geminiRawResponse;      // texto completo retornado pelo Gemini
    private boolean companyWasMentioned;
    private String summary;                // resumo curto (opcional)
    private LocalDateTime analyzedAt;

    private List<MentionedBrand> mentionedBrands;
}