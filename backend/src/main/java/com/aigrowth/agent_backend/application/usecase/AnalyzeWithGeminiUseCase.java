package com.aigrowth.agent_backend.application.usecase;

import com.aigrowth.agent_backend.application.dto.AnalyzeRequest;
import com.aigrowth.agent_backend.application.dto.DiagnosisResultResponse;
import com.aigrowth.agent_backend.application.dto.MentionedBrandResponse;
import com.aigrowth.agent_backend.domain.model.DiagnosisStatus;
import com.aigrowth.agent_backend.domain.model.Sentiment;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger;
import com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzeWithGeminiUseCase {

    private final ChatClient chatClient;
    private final DiagnosisLogger diagnosisLogger;
    private final DiagnosisContextStore contextStore;

    // Matches domains like company.com.br, www.site.com, https://site.com.br, etc.
    private static final Pattern SITE_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?([a-zA-Z0-9][a-zA-Z0-9\\-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})?)"
    );

    // Matches domain + score: "company.com.br (score: 85)" or "company.com.br (85)"
    private static final Pattern SITE_WITH_SCORE_PATTERN = Pattern.compile(
            "([a-zA-Z0-9][a-zA-Z0-9\\-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}(?:\\.[a-zA-Z]{2,})?)\\s*\\(?score:\\s*(\\d+)\\)?"
    );

    public DiagnosisResultResponse execute(AnalyzeRequest request) {

        String userQuery = request.getSelectedQuery();

        // Retrieve domain and language from stored context
        SiteContent siteContent = contextStore.get(request.getDiagnosisId());
        String userDomain = siteContent != null
                ? normalizeDomain(siteContent.getDomain())
                : "";
        String lang = siteContent != null && siteContent.getLanguage() != null
                ? siteContent.getLanguage()
                : "pt";
        String langInstruction = getLanguageInstruction(lang);

        String prompt = """
        Você é uma Inteligência Artificial respondendo a uma consulta real de um usuário.

        IDIOMA OBRIGATÓRIO: %s

        Pergunta do usuário:
        "%s"

        Responda de forma natural, útil e profissional no idioma especificado acima.

        Regras obrigatórias:
        - Mencione empresas relevantes que atendam a essa necessidade
        - Para cada empresa mencionada, inclua obrigatoriamente o site oficial (ex: empresa.com.br)
        - IMPORTANTE: Ao mencionar cada domínio, adicione um score de 0-100 no formato: dominio.com.br (score: 85)
        - 100: Recomendação muito forte / melhor opção
        - 75-80: Boa recomendação / opção sólida
        - 50-60: Opção interessante / alternativa válida
        - 25-40: Opção aceitável / menção de passagem
        - 0-20: Não recomendado / menção negativa
        - Cite entre 3 e 8 empresas com seus respectivos sites e scores
        - Inclua os sites no formato domínio puro: empresa.com.br ou www.empresa.com
        - Não use markdown, não use asteriscos, não use listas com traços
        - Responda em parágrafos corridos
        - REGRA CRÍTICA: Ao mencionar qualquer empresa, verifique que o domínio citado pertence REALMENTE àquela empresa. Não confunda empresas diferentes que têm nomes parecidos. Use apenas domínios que você tem certeza que são corretos.
        """.formatted(langInstruction, userQuery);

        try {
            String rawContent = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Gemini response: {}", rawContent);

            List<MentionedBrandResponse> brands = extractBrands(rawContent);

            log.info("Domains extracted from response: {}", brands.stream()
                    .map(MentionedBrandResponse::getBrandName).toList());

            // Check if the user's brand was mentioned (by name, not just domain)
            String brandName = siteContent != null ? siteContent.getBrandName() : null;
            boolean companyMentioned = isBrandMentioned(rawContent, brands, userDomain, brandName);

            // If brand was mentioned by name but with wrong domain, fix the brand list
            if (companyMentioned && !userDomain.isEmpty()) {
                brands = fixUserBrandDomain(brands, rawContent, userDomain, brandName);
            }

            log.info("Company mentioned: {} (brandName: {}, domain: {})", companyMentioned, brandName, userDomain);

            String summary = companyMentioned
                    ? "Sua marca foi mencionada como uma opção recomendada!"
                    : "Sua marca ainda não aparece entre as principais recomendações das IAs.";

            DiagnosisResultResponse result = DiagnosisResultResponse.builder()
                    .diagnosisId(request.getDiagnosisId())
                    .status(DiagnosisStatus.COMPLETED.name())
                    .geminiResponse(rawContent)
                    .companyWasMentioned(companyMentioned)
                    .summary(summary)
                    .mentionedBrands(brands)
                    .analyzedAt(LocalDateTime.now())
                    .build();

            diagnosisLogger.logAnalysis(request.getDiagnosisId(), request, prompt, rawContent, result);
            contextStore.saveResult(request.getDiagnosisId(), result);

            return result;

        } catch (Exception e) {
            log.error("Error calling Gemini", e);
            throw new RuntimeException("Failed to communicate with Gemini", e);
        }
    }

    private List<MentionedBrandResponse> extractBrands(String rawContent) {
        List<MentionedBrandResponse> brands = new ArrayList<>();

        // First, try to extract domains with scores
        Matcher scoresMatcher = SITE_WITH_SCORE_PATTERN.matcher(rawContent);
        while (scoresMatcher.find()) {
            String site = scoresMatcher.group(1).toLowerCase();
            String scoreStr = scoresMatcher.group(2);

            if (isGenericDomain(site)) continue;
            if (brands.stream().anyMatch(b -> b.getBrandName().equalsIgnoreCase(site))) continue;

            int rawScore = Integer.parseInt(scoreStr);
            int normalizedScore = normalizeScore(rawScore);
            String sentiment = calculateSentiment(normalizedScore);

            brands.add(MentionedBrandResponse.builder()
                    .brandName(site)
                    .sentiment(sentiment)
                    .score(normalizedScore)
                    .build());

            log.debug("Domain extracted with score: {} → {} (sentiment: {})", site, normalizedScore, sentiment);
        }

        // If no scored domains found, fallback to pattern without scores
        if (brands.isEmpty()) {
            Matcher matcher = SITE_PATTERN.matcher(rawContent);
            while (matcher.find()) {
                String site = matcher.group(1).toLowerCase();

                if (isGenericDomain(site)) continue;
                if (brands.stream().anyMatch(b -> b.getBrandName().equalsIgnoreCase(site))) continue;

                brands.add(MentionedBrandResponse.builder()
                        .brandName(site)
                        .sentiment(Sentiment.NEUTRAL.name())
                        .score(0)
                        .build());
            }
        }

        return brands;
    }

    /**
     * Normalizes score from 0-100 to discrete levels: 0, 25, 50, 75, 100
     * Example: 82 → 100, 58 → 50, 23 → 25
     */
    private int normalizeScore(int rawScore) {
        // Clamp between 0 and 100
        int clamped = Math.max(0, Math.min(100, rawScore));
        // Round to the nearest multiple of 25
        return Math.round(clamped / 25.0f) * 25;
    }

    private boolean isGenericDomain(String domain) {
        return domain.matches(".*(google|youtube|wikipedia|instagram|facebook|twitter|linkedin|whatsapp|gmail|outlook|hotmail|apple|microsoft|amazon|mercadolivre|shopify)\\..*");
    }

    /**
     * Calculates sentiment based on the normalized score
     * 75-100: POSITIVE (strong recommendation)
     * 50: NEUTRAL (interesting option)
     * 0-25: NEGATIVE (acceptable or not recommended)
     */
    private String calculateSentiment(int normalizedScore) {
        if (normalizedScore >= 75) {
            return Sentiment.POSITIVE.name();
        } else if (normalizedScore == 50) {
            return Sentiment.NEUTRAL.name();
        } else {
            return Sentiment.NEGATIVE.name();
        }
    }

    /**
     * Normalizes the user's domain by removing www. and keeping only the base domain
     * Example: www.mailchimp.com → mailchimp.com
     */
    private String normalizeDomain(String domain) {
        if (domain == null) return "";
        return domain.toLowerCase()
                .replaceFirst("^(https?://)?", "")
                .replaceFirst("^www\\.", "")
                .replaceFirst("/$", "");
    }

    /**
     * Checks if the user's brand was mentioned in Gemini's response.
     * Uses 3 layers of detection:
     *   1. Exact domain match in extracted brands (e.g., nomadglobal.com)
     *   2. Brand name match in the raw response text (e.g., "Nomad")
     *   3. Domain-without-TLD match in the raw response text (e.g., "nomadglobal")
     */
    private boolean isBrandMentioned(String rawContent, List<MentionedBrandResponse> brands,
                                     String userDomain, String brandName) {
        // Layer 1: Exact domain match (existing behavior)
        if (brands.stream().anyMatch(b -> b.getBrandName().equalsIgnoreCase(userDomain))) {
            return true;
        }

        String lowerResponse = rawContent.toLowerCase();

        // Layer 2: Brand name match (e.g., "Nomad" found in response)
        if (brandName != null && brandName.length() >= 3) {
            String lowerBrand = brandName.toLowerCase();
            // Match as whole word to avoid false positives (e.g., "best" in "the best option")
            Pattern wordPattern = Pattern.compile("\\b" + Pattern.quote(lowerBrand) + "\\b",
                    Pattern.CASE_INSENSITIVE);
            if (wordPattern.matcher(rawContent).find()) {
                log.info("Brand detected by name match: '{}' found in response", brandName);
                return true;
            }
        }

        // Layer 3: Domain-without-TLD match (e.g., "nomadglobal" found in response)
        if (!userDomain.isEmpty()) {
            String domainWithoutTld = userDomain.replaceFirst("\\.[a-z]{2,}(\\.[a-z]{2,})?$", "");
            if (domainWithoutTld.length() >= 3 && lowerResponse.contains(domainWithoutTld.toLowerCase())) {
                log.info("Brand detected by domain-name match: '{}' found in response", domainWithoutTld);
                return true;
            }
        }

        return false;
    }

    /**
     * Fixes the brand list when the user's brand was mentioned by name but Gemini
     * used a wrong domain. Replaces the incorrect domain with the user's actual domain.
     *
     * Example: Gemini cited "nomad.io (score: 85)" but the user's company is nomadglobal.com.
     *          The brand name "Nomad" matches, so we replace nomad.io → nomadglobal.com.
     */
    private List<MentionedBrandResponse> fixUserBrandDomain(List<MentionedBrandResponse> brands,
                                                             String rawContent, String userDomain,
                                                             String brandName) {
        // If user's domain is already in the list, nothing to fix
        if (brands.stream().anyMatch(b -> b.getBrandName().equalsIgnoreCase(userDomain))) {
            return brands;
        }

        if (brandName == null || brandName.length() < 3) {
            return brands;
        }

        String lowerBrand = brandName.toLowerCase();
        List<MentionedBrandResponse> fixed = new ArrayList<>();
        boolean replaced = false;

        for (MentionedBrandResponse brand : brands) {
            // Check if this brand entry corresponds to the user's company (wrong domain, right name)
            String domainWithoutTld = brand.getBrandName()
                    .replaceFirst("\\.[a-z]{2,}(\\.[a-z]{2,})?$", "");
            boolean nameMatchesBrand = domainWithoutTld.toLowerCase().contains(lowerBrand)
                    || lowerBrand.contains(domainWithoutTld.toLowerCase());

            if (!replaced && nameMatchesBrand) {
                // Replace the wrong domain with the user's actual domain
                log.info("Fixing brand domain: {} → {} (matched by brand name '{}')",
                        brand.getBrandName(), userDomain, brandName);
                fixed.add(MentionedBrandResponse.builder()
                        .brandName(userDomain)
                        .sentiment(brand.getSentiment())
                        .score(brand.getScore())
                        .build());
                replaced = true;
            } else {
                fixed.add(brand);
            }
        }

        return fixed;
    }

    private String getLanguageInstruction(String lang) {
        return switch (lang) {
            case "en" -> "Respond entirely in English. The full response must be in English.";
            case "es" -> "Responde completamente en español. Toda la respuesta debe estar en español.";
            default   -> "Responda inteiramente em português. Toda a resposta deve estar em português.";
        };
    }
}