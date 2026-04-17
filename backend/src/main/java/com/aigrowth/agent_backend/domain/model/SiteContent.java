package com.aigrowth.agent_backend.domain.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteContent {

    private String domain;

    // Brand name extracted from the homepage title (e.g., "Nomad", "HubSpot")
    private String brandName;

    // Language selected by the user (pt, en, es)
    private String language;

    // Text extracted from the homepage (null if access failed)
    private String homeText;

    // true if the homepage was successfully accessed
    private boolean scraped;

    /**
     * Returns a short summary of the homepage to send to Gemini (saves tokens).
     */
    public String toPromptContext() {
        if (!scraped || homeText == null || homeText.isBlank()) {
            return "Domínio: " + domain + "\n(Não foi possível acessar o site — use o domínio como contexto.)";
        }

        return "Domínio: " + domain + "\n\n" + truncate(homeText, 400);
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > maxChars ? text.substring(0, maxChars) + "..." : text;
    }
}
