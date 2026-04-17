package com.aigrowth.agent_backend.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisStartRequest {

    @NotBlank(message = "O domínio é obrigatório")
    private String domain;

    @Email(message = "Email inválido")
    private String email;

    private String clientBrandName;   // nome da empresa (opcional)

    private String language = "pt";   // pt, en, es
}