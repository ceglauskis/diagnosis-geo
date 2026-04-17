package com.aigrowth.agent_backend.application.usecase;

import com.aigrowth.agent_backend.application.dto.QueryResponse;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger;
import com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateQueriesUseCase {

    private final ChatClient chatClient;
    private final DiagnosisContextStore contextStore;
    private final DiagnosisLogger diagnosisLogger;

    public List<QueryResponse> execute(String diagnosisId) {

        SiteContent siteContent = contextStore.get(diagnosisId);

        if (siteContent == null) {
            log.warn("Context not found for diagnosisId: {}. Returning generic fallback queries.", diagnosisId);
            return fallbackQueries();
        }

        String context = siteContent.toPromptContext();
        String lang = siteContent.getLanguage() != null ? siteContent.getLanguage() : "pt";
        String langInstruction = getLanguageInstruction(lang);

        log.info("Generating queries with Gemini for domain: {} (language: {})", siteContent.getDomain(), lang);

        String prompt = """
                Você é um especialista em AEO (Answer Engine Optimization).

                IDIOMA OBRIGATÓRIO: %s

                Sua tarefa: analisar o site abaixo, identificar os PRODUTOS e SERVIÇOS REAIS que ele oferece,
                e gerar 5 perguntas que um usuário faria a uma IA (ChatGPT, Gemini, Perplexity) cuja RESPOSTA
                naturalmente mencionaria esta empresa.

                Passo 1 - Analise o site e identifique:
                - Quais são os produtos/serviços principais? (ex: buscador, email, anúncios, streaming, CRM, etc.)
                - Em qual categoria/setor cada produto compete?

                Passo 2 - Para cada produto/serviço identificado, pense:
                "Se alguém perguntasse isso a uma IA, esta empresa seria mencionada na resposta?"
                Se a resposta for NÃO, descarte e tente outra pergunta.

                Passo 3 - Gere EXATAMENTE 5 perguntas seguindo estas regras:
                - Cubra diferentes produtos/serviços da empresa (não repita o mesmo ângulo)
                - As perguntas devem ser naturais, como uma pessoa real perguntaria
                - NÃO mencione o nome da empresa nas perguntas
                - Cada pergunta deve ter alta probabilidade de que uma IA cite esta empresa na resposta
                - Adicione um motivo curto (máximo 10 palavras) explicando por que a empresa seria citada
                - TODAS as perguntas e motivos devem estar no idioma especificado acima

                Formato obrigatório (SOMENTE 5 linhas, sem numeração, sem prefixo):
                PERGUNTA | MOTIVO

                Informações do site:
                %s
                """.formatted(langInstruction, context);

        try {
            String rawResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Gemini generated queries for {}", siteContent.getDomain());

            List<QueryResponse> queries = parseQueries(rawResponse);

            // Log step 2
            diagnosisLogger.logQueriesGenerated(diagnosisId, prompt, queries);

            return queries;

        } catch (Exception e) {
            log.error("Error generating queries with Gemini for {}: {}", siteContent.getDomain(), e.getMessage());
            return fallbackQueries();
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private List<QueryResponse> parseQueries(String rawResponse) {
        List<QueryResponse> parsed = Arrays.stream(rawResponse.split("\n"))
                .map(String::trim)
                .filter(line -> line.contains("|"))           // must have separator
                .filter(line -> !line.startsWith("*"))        // skip markdown bold/bullet
                .filter(line -> !line.startsWith("#"))        // skip headers
                .filter(line -> line.length() > 15)
                .limit(5)
                .map(line -> {
                    String[] parts = line.split("\\|", 2);
                    String text   = parts[0].trim().replaceAll("^\\*+|\\*+$", ""); // remove residual **
                    String reason = parts.length > 1 ? parts[1].trim() : "";
                    return QueryResponse.builder()
                            .id(UUID.randomUUID().toString())
                            .text(text)
                            .reason(reason)
                            .isCustom(false)
                            .build();
                })
                .collect(Collectors.toList());

        // If fewer than 3 valid queries were parsed, use fallback
        return parsed.size() >= 3 ? parsed : fallbackQueries();
    }

    private List<QueryResponse> fallbackQueries() {
        return List.of(
                buildQuery("Quais são as melhores empresas do setor para contratar?",        "Busca frequente por fornecedor no segmento"),
                buildQuery("Quem são os principais fornecedores recomendados por especialistas?", "Intenção de compra assistida por IA"),
                buildQuery("Quais empresas têm melhor reputação nesse segmento?",            "Pesquisa de reputação e autoridade de marca"),
                buildQuery("Qual empresa devo contratar para esse tipo de serviço?",          "Decisão de compra direta via IA"),
                buildQuery("Quem são os líderes de mercado nessa área?",                     "Mapeamento de concorrentes pelo comprador")
        );
    }

    private QueryResponse buildQuery(String text, String reason) {
        return QueryResponse.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .reason(reason)
                .isCustom(false)
                .build();
    }

    private String getLanguageInstruction(String lang) {
        return switch (lang) {
            case "en" -> "Respond entirely in English. All questions and reasons must be in English.";
            case "es" -> "Responde completamente en español. Todas las preguntas y motivos deben estar en español.";
            default   -> "Responda inteiramente em português. Todas as perguntas e motivos devem estar em português.";
        };
    }
}