package com.aigrowth.agent_backend.usecase;

import com.aigrowth.agent_backend.application.dto.QueryResponse;
import com.aigrowth.agent_backend.application.usecase.GenerateQueriesUseCase;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger;
import com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateQueriesUseCaseTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callSpec;
    @Mock private DiagnosisContextStore contextStore;
    @Mock private DiagnosisLogger diagnosisLogger;

    private GenerateQueriesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateQueriesUseCase(chatClient, contextStore, diagnosisLogger);
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
                .homeText("Empresa de software para gestão empresarial.")
                .build();
        when(contextStore.get(anyString())).thenReturn(siteContent);
    }

    // ==================== parseQueries ====================

    @Nested
    @DisplayName("parseQueries — correct format")
    class ParseQueriesTests {

        @Test
        @DisplayName("should parse 5 lines in QUESTION | REASON format")
        void shouldParseFiveValidLines() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    Qual o melhor CRM para pequenas empresas? | Líder de mercado no segmento
                    Como integrar vendas e marketing em uma plataforma? | Produto principal da empresa
                    Quais ferramentas de automação de marketing existem? | Produto central da empresa
                    Como gerenciar leads e pipeline de vendas? | Core feature da plataforma
                    Qual software usar para acompanhar o funil de vendas? | Recurso fundamental do CRM
                    """);

            List<QueryResponse> result = useCase.execute("id-1");

            assertThat(result).hasSize(5);
            assertThat(result.get(0).getText()).isEqualTo("Qual o melhor CRM para pequenas empresas?");
            assertThat(result.get(0).getReason()).isEqualTo("Líder de mercado no segmento");
        }

        @Test
        @DisplayName("should ignore lines without the | separator")
        void shouldIgnoreLinesWithoutPipe() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    Esta linha não tem separador e deve ser ignorada
                    Qual o melhor CRM? | Líder de mercado
                    Outra linha sem pipe aqui também
                    Como integrar vendas e marketing? | Produto principal
                    Quais ferramentas de automação existem? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil de vendas? | Recurso central
                    """);

            List<QueryResponse> result = useCase.execute("id-2");

            assertThat(result).hasSize(5);
            result.forEach(q -> assertThat(q.getText()).doesNotContain("sem pipe").doesNotContain("sem separador"));
        }

        @Test
        @DisplayName("should ignore markdown bold (**) lines")
        void shouldIgnoreMarkdownBoldLines() {
            mockContext("binance.com", "en");
            mockGeminiResponse("""
                    **Step 1: Identify Core Products/Services and Sectors**
                    Based on the domain "binance.com," the primary products and services are:
                    * **Cryptocurrency Exchange Platform:** This is the core offering.
                    What is the best crypto exchange for beginners? | Market leader globally
                    Which platform offers the lowest trading fees? | Core competitive advantage
                    How to buy Bitcoin safely online? | Primary use case for the platform
                    What is the best app to trade altcoins? | Wide variety of tokens offered
                    Where can I stake crypto to earn rewards? | Staking feature availability
                    """);

            List<QueryResponse> result = useCase.execute("id-3");

            assertThat(result).hasSize(5);
            result.forEach(q -> {
                assertThat(q.getText()).doesNotContain("**");
                assertThat(q.getText()).doesNotContain("Step 1");
                assertThat(q.getText()).doesNotContain("primary products");
            });
        }

        @Test
        @DisplayName("should ignore lines starting with #")
        void shouldIgnoreHashHeaders() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    # Análise do site
                    ## Produtos identificados
                    Qual o melhor CRM? | Líder de mercado
                    Como integrar vendas e marketing? | Produto principal
                    Quais ferramentas de automação existem? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil? | Recurso central
                    """);

            List<QueryResponse> result = useCase.execute("id-4");

            assertThat(result).hasSize(5);
            result.forEach(q -> assertThat(q.getText()).doesNotStartWith("#"));
        }

        @Test
        @DisplayName("should remove residual ** from query text")
        void shouldRemoveResidualAsterisks() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    **Qual o melhor CRM?** | Líder de mercado
                    Como integrar vendas e marketing? | Produto principal
                    Quais ferramentas de automação existem? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil? | Recurso central
                    """);

            // Line starts with *, so it's filtered — fallback should be returned
            // Only 4 valid lines (without *), so fallback is active
            List<QueryResponse> result = useCase.execute("id-5");

            assertThat(result).isNotEmpty();
            result.forEach(q -> assertThat(q.getText()).doesNotContain("**"));
        }
    }

    // ==================== Fallback ====================

    @Nested
    @DisplayName("Fallback behavior")
    class FallbackTests {

        @Test
        @DisplayName("should return fallback when diagnosisId does not exist in store")
        void shouldReturnFallbackWhenContextNotFound() {
            when(contextStore.get("id-inexistente")).thenReturn(null);

            List<QueryResponse> result = useCase.execute("id-inexistente");

            assertThat(result).hasSize(5);
            assertThat(result).allMatch(q -> !q.getText().isBlank());
            verifyNoInteractions(chatClient);
        }

        @Test
        @DisplayName("should return fallback when Gemini throws exception")
        void shouldReturnFallbackWhenGeminiFails() {
            mockContext("hubspot.com", "pt");
            when(chatClient.prompt()).thenThrow(new RuntimeException("Gemini offline"));

            List<QueryResponse> result = useCase.execute("id-gemini-fail");

            assertThat(result).hasSize(5);
            assertThat(result).allMatch(q -> !q.getText().isBlank());
        }

        @Test
        @DisplayName("should return fallback when Gemini returns fewer than 3 valid queries")
        void shouldReturnFallbackWhenTooFewQueriesParsed() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    Não consegui identificar produtos suficientes.
                    Esta análise é incompleta para gerar perguntas.
                    """);

            List<QueryResponse> result = useCase.execute("id-few");

            assertThat(result).hasSize(5);
        }

        @Test
        @DisplayName("fallback queries should not have isCustom = true")
        void fallbackQueriesShouldNotBeCustom() {
            when(contextStore.get(anyString())).thenReturn(null);

            List<QueryResponse> result = useCase.execute("id-any");

            assertThat(result).allMatch(q -> !q.isCustom());
        }
    }

    // ==================== Language ====================

    @Nested
    @DisplayName("Language instruction in prompt")
    class LanguageTests {

        @Test
        @DisplayName("should include English instruction in prompt when language=en")
        void shouldIncludeEnglishInstructionForEnLanguage() {
            mockContext("binance.com", "en");
            mockGeminiResponse("""
                    What is the best crypto exchange? | Market leader globally
                    How to buy Bitcoin safely? | Primary use case
                    Which platform has lowest trading fees? | Key competitive advantage
                    How to trade altcoins online? | Wide token variety
                    Where to stake crypto for rewards? | Staking feature
                    """);

            useCase.execute("id-en");

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(requestSpec).user(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Respond entirely in English");
        }

        @Test
        @DisplayName("should include Spanish instruction in prompt when language=es")
        void shouldIncludeSpanishInstructionForEsLanguage() {
            mockContext("mercadolibre.com", "es");
            mockGeminiResponse("""
                    ¿Cuál es la mejor plataforma de ecommerce? | Líder regional en ventas
                    ¿Dónde comprar productos electrónicos baratos? | Gran catálogo disponible
                    ¿Cómo vender productos por internet? | Plataforma para vendedores
                    ¿Cuál es el marketplace más grande de América Latina? | Mayor marketplace regional
                    ¿Cómo hacer pagos online seguros? | Sistema de pagos integrado
                    """);

            useCase.execute("id-es");

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(requestSpec).user(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Responde completamente en español");
        }

        @Test
        @DisplayName("should default to Portuguese when language is null")
        void shouldDefaultToPortugueseWhenLanguageIsNull() {
            SiteContent siteContent = SiteContent.builder()
                    .domain("hubspot.com")
                    .language(null)
                    .scraped(true)
                    .homeText("CRM software.")
                    .build();
            when(contextStore.get(anyString())).thenReturn(siteContent);
            mockGeminiResponse("""
                    Qual o melhor CRM? | Líder de mercado
                    Como integrar vendas? | Produto principal
                    Quais ferramentas de automação? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil? | Recurso central
                    """);

            useCase.execute("id-null-lang");

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(requestSpec).user(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Responda inteiramente em português");
        }
    }

    // ==================== Output structure ====================

    @Nested
    @DisplayName("Output structure")
    class OutputStructureTests {

        @Test
        @DisplayName("each query should have a unique UUID as id")
        void eachQueryShouldHaveUniqueId() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    Qual o melhor CRM? | Líder de mercado
                    Como integrar vendas? | Produto principal
                    Quais ferramentas de automação? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil? | Recurso central
                    """);

            List<QueryResponse> result = useCase.execute("id-uuid");

            assertThat(result).extracting("id").doesNotHaveDuplicates();
            assertThat(result).allMatch(q -> q.getId() != null && !q.getId().isBlank());
        }

        @Test
        @DisplayName("Gemini-generated queries should have isCustom = false")
        void geminiQueriesShouldNotBeCustom() {
            mockContext("hubspot.com", "pt");
            mockGeminiResponse("""
                    Qual o melhor CRM? | Líder de mercado
                    Como integrar vendas? | Produto principal
                    Quais ferramentas de automação? | Core feature
                    Como gerenciar leads? | Feature importante
                    Qual software para funil? | Recurso central
                    """);

            List<QueryResponse> result = useCase.execute("id-custom");

            assertThat(result).allMatch(q -> !q.isCustom());
        }

        @Test
        @DisplayName("should return at most 5 queries even if Gemini returns more")
        void shouldLimitToFiveQueries() {
            mockContext("google.com", "en");
            mockGeminiResponse("""
                    What is the best search engine? | Market leader
                    How to use email for free? | Gmail service
                    Best cloud storage solution? | Google Drive
                    How to run online ads? | Google Ads platform
                    Best video platform for content? | YouTube owned by Google
                    Where to find maps online? | Google Maps
                    Best productivity suite for business? | Google Workspace
                    """);

            List<QueryResponse> result = useCase.execute("id-limit");

            assertThat(result).hasSize(5);
        }
    }
}
