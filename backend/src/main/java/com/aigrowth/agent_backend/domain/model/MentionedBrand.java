package com.aigrowth.agent_backend.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentionedBrand {

    private UUID id;
    private UUID diagnosisResultId;
    private String brandName;
    private Sentiment sentiment;     // POSITIVE, NEUTRAL, NEGATIVE
    private int score;               // 0 a 100
    private String contextSnippet;   // trecho do texto onde foi mencionada (opcional)
}