package com.aigrowth.agent_backend.usecase;

import com.aigrowth.agent_backend.application.dto.AnalyzeRequest;
import com.aigrowth.agent_backend.application.dto.DiagnosisResultResponse;
import com.aigrowth.agent_backend.application.usecase.AnalyzeWithGeminiUseCase;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger;
import com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyzeWithGeminiUseCaseTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;
    @Mock private DiagnosisLogger diagnosisLogger;
    @Mock private DiagnosisContextStore contextStore;

    private AnalyzeWithGeminiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AnalyzeWithGeminiUseCase(chatClient, diagnosisLogger, contextStore);
    }

    private void mockGeminiResponse(String response) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn(response);
    }

    private void mockContext(String domain, String language) {
        SiteContent siteContent = SiteContent.builder()
                .domain(domain)
                .language(language)
                .scraped(true)
                .homeText("some content")
                .build();
        when(contextStore.get(anyString())).thenReturn(siteContent);
    }

    private AnalyzeRequest buildRequest(String diagnosisId, String query) {
        return AnalyzeRequest.builder()
                .diagnosisId(diagnosisId)
                .selectedQuery(query)
                .build();
    }

    // ==================== companyWasMentioned ====================

    @Nested
    @DisplayName("companyWasMentioned detection")
    class CompanyMentionedTests {

        @Test
        @DisplayName("should detect brand when domain appears in the response")
        void shouldDetectWhenBrandIsMentioned() {
            mockContext("mailchimp.com", "pt");
            mockGeminiResponse("Para email marketing, recomendo mailchimp.com (score: 100) como melhor opção.");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-1", "melhor ferramenta de email marketing?"));

            assertThat(result.isCompanyWasMentioned()).isTrue();
        }

        @Test
        @DisplayName("should return false when brand was not mentioned")
        void shouldReturnFalseWhenBrandNotMentioned() {
            mockContext("minhaMarca.com.br", "pt");
            mockGeminiResponse("Para email marketing, recomendo mailchimp.com (score: 100) e rdstation.com.br (score: 75).");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-2", "melhor ferramenta de email marketing?"));

            assertThat(result.isCompanyWasMentioned()).isFalse();
        }

        @Test
        @DisplayName("should detect brand with www. prefix in user domain")
        void shouldDetectBrandWithWwwPrefix() {
            mockContext("www.mailchimp.com", "pt");
            mockGeminiResponse("Recomendo mailchimp.com (score: 90) como a melhor opção.");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-3", "email marketing?"));

            assertThat(result.isCompanyWasMentioned()).isTrue();
        }

        @Test
        @DisplayName("should detect brand with https:// prefix in user domain")
        void shouldDetectBrandWithHttpsPrefix() {
            mockContext("https://mailchimp.com", "pt");
            mockGeminiResponse("Recomendo mailchimp.com (score: 90).");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-4", "email marketing?"));

            assertThat(result.isCompanyWasMentioned()).isTrue();
        }

        @Test
        @DisplayName("should compare domain case-insensitively")
        void shouldCompareDomainCaseInsensitive() {
            mockContext("Mailchimp.COM", "pt");
            mockGeminiResponse("Recomendo mailchimp.com (score: 90).");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-5", "email marketing?"));

            assertThat(result.isCompanyWasMentioned()).isTrue();
        }
    }

    // ==================== Score normalization ====================

    @Nested
    @DisplayName("Score normalization (0, 25, 50, 75, 100)")
    class ScoreNormalizationTests {

        @Test
        @DisplayName("score 100 → 100")
        void score100() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 100).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(100);
        }

        @Test
        @DisplayName("score 82 → 75")
        void score82NormalizesTo75() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 82).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(75);
        }

        @Test
        @DisplayName("score 63 → 75 (round(63/25)=3, 3*25=75)")
        void score63NormalizesTo75() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 63).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(75);
        }

        @Test
        @DisplayName("score 50 → 50 (exact boundary)")
        void score50NormalizesTo50() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 50).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(50);
        }

        @Test
        @DisplayName("score 37 → 25")
        void score37NormalizesTo25() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 37).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(25);
        }

        @Test
        @DisplayName("score 10 → 0")
        void score10NormalizesTo0() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 10).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getScore()).isEqualTo(0);
        }
    }

    // ==================== Sentiment calculation ====================

    @Nested
    @DisplayName("Sentiment calculation based on score")
    class SentimentTests {

        @Test
        @DisplayName("score 100 → POSITIVE")
        void score100isPositive() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 100).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getSentiment()).isEqualTo("POSITIVE");
        }

        @Test
        @DisplayName("score 75 → POSITIVE (boundary)")
        void score75isPositive() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 75).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getSentiment()).isEqualTo("POSITIVE");
        }

        @Test
        @DisplayName("score 50 → NEUTRAL")
        void score50isNeutral() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 50).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getSentiment()).isEqualTo("NEUTRAL");
        }

        @Test
        @DisplayName("score 25 → NEGATIVE")
        void score25isNegative() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 25).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getSentiment()).isEqualTo("NEGATIVE");
        }

        @Test
        @DisplayName("score 0 → NEGATIVE")
        void score0isNegative() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("recomendo site-a.com (score: 0).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands().get(0).getSentiment()).isEqualTo("NEGATIVE");
        }
    }

    // ==================== Generic domain filtering ====================

    @Nested
    @DisplayName("Generic domain filtering")
    class GenericDomainTests {

        @Test
        @DisplayName("should filter google.com from brand list")
        void shouldFilterGoogle() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Veja em google.com (score: 90) ou use site-a.com (score: 80).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands())
                    .extracting("brandName")
                    .doesNotContain("google.com")
                    .contains("site-a.com");
        }

        @Test
        @DisplayName("should filter facebook.com, youtube.com and instagram.com")
        void shouldFilterSocialMedia() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Veja facebook.com (score: 90), youtube.com (score: 80), instagram.com (score: 70), site-a.com (score: 60).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands())
                    .extracting("brandName")
                    .doesNotContain("facebook.com", "youtube.com", "instagram.com")
                    .contains("site-a.com");
        }

        @Test
        @DisplayName("should not filter legitimate domains containing generic substrings")
        void shouldNotFilterLegitimateDomainsWithGenericSubstrings() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Recomendo amazonia.com.br (score: 80).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands())
                    .extracting("brandName")
                    .contains("amazonia.com.br");
        }
    }

    // ==================== Brand extraction ====================

    @Nested
    @DisplayName("Brand extraction from Gemini response")
    class BrandExtractionTests {

        @Test
        @DisplayName("should extract multiple domains with scores")
        void shouldExtractMultipleBrands() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("""
                    Para CRM recomendo hubspot.com (score: 95), salesforce.com (score: 90) e pipedrive.com (score: 75).
                    """);
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands()).hasSize(3);
            assertThat(result.getMentionedBrands())
                    .extracting("brandName")
                    .containsExactly("hubspot.com", "salesforce.com", "pipedrive.com");
        }

        @Test
        @DisplayName("should not duplicate the same domain")
        void shouldNotDuplicateDomains() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Recomendo hubspot.com (score: 95). hubspot.com (score: 90) é ótimo.");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands())
                    .extracting("brandName")
                    .containsOnlyOnce("hubspot.com");
        }

        @Test
        @DisplayName("should return empty list when response contains no scored domains")
        void shouldReturnEmptyListWhenNoDomainsFound() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Não encontrei empresas relevantes para essa busca.");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getMentionedBrands()).isEmpty();
        }
    }

    // ==================== Error handling ====================

    @Nested
    @DisplayName("Error handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should throw RuntimeException when Gemini fails")
        void shouldThrowWhenGeminiFails() {
            mockContext("brand.com", "pt");
            when(chatClient.prompt()).thenThrow(new RuntimeException("API offline"));

            assertThatThrownBy(() -> useCase.execute(buildRequest("id", "query")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to communicate with Gemini");
        }

        @Test
        @DisplayName("should work without context in store (non-existent diagnosisId)")
        void shouldWorkWithoutContextStore() {
            when(contextStore.get(anyString())).thenReturn(null);
            mockGeminiResponse("Recomendo hubspot.com (score: 90).");

            DiagnosisResultResponse result = useCase.execute(buildRequest("id-inexistente", "query"));

            assertThat(result).isNotNull();
            assertThat(result.isCompanyWasMentioned()).isFalse();
        }
    }

    // ==================== Status and persistence ====================

    @Nested
    @DisplayName("Result status and persistence")
    class ResultPersistenceTests {

        @Test
        @DisplayName("should return COMPLETED status")
        void shouldReturnCompletedStatus() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Recomendo hubspot.com (score: 90).");
            DiagnosisResultResponse result = useCase.execute(buildRequest("id", "query"));
            assertThat(result.getStatus()).isEqualTo("COMPLETED");
        }

        @Test
        @DisplayName("should save result to contextStore")
        void shouldSaveResultToContextStore() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Recomendo hubspot.com (score: 90).");
            useCase.execute(buildRequest("id-save", "query"));
            verify(contextStore).saveResult(eq("id-save"), any(DiagnosisResultResponse.class));
        }

        @Test
        @DisplayName("should log the analysis via DiagnosisLogger")
        void shouldLogAnalysis() {
            mockContext("brand.com", "pt");
            mockGeminiResponse("Recomendo hubspot.com (score: 90).");
            useCase.execute(buildRequest("id-log", "query"));
            verify(diagnosisLogger).logAnalysis(eq("id-log"), any(), anyString(), anyString(), any());
        }
    }
}
