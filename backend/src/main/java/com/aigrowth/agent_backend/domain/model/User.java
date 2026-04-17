package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private UUID id;
    private String email;
    private String name;
    private String companyName;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}