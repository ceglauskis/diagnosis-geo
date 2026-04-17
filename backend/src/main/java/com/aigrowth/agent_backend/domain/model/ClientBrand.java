package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBrand {

    private UUID id;
    private UUID userId;            // dono da marca
    private String domain;          // ex: "seusite.com.br" ou "seusite.com"
    private String name;            // nome da empresa (ex: "Tech Cabos")
    private String industry;        // ex: "Structured Cabling", "Marketing Digital"
    private LocalDateTime createdAt;
}
