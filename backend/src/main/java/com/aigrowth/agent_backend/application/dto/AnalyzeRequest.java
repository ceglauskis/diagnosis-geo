package com.aigrowth.agent_backend.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeRequest {

    @NotBlank
    private String diagnosisId;     // UUID as String

    private String selectedQuery;   // query selected by the user

    private String customQuery;     // if the user wrote their own
}