package com.aigrowth.agent_backend.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentionedBrandResponse {
    private String brandName;
    private String sentiment;   // "POSITIVE", "NEUTRAL", "NEGATIVE"
    private int score;
    private String contextSnippet;
}