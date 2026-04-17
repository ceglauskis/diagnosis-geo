package com.aigrowth.agent_backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResultResponse {

    private String diagnosisId;
    private String status;
    private String geminiResponse;
    private boolean companyWasMentioned;
    private String summary;
    private List<MentionedBrandResponse> mentionedBrands;
    private LocalDateTime analyzedAt;
}