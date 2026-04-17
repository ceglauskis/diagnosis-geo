package com.aigrowth.agent_backend.application.usecase;

import com.aigrowth.agent_backend.application.dto.DiagnosisStartRequest;
import com.aigrowth.agent_backend.application.dto.DiagnosisStartResponse;
import com.aigrowth.agent_backend.domain.model.DiagnosisStatus;
import com.aigrowth.agent_backend.domain.model.SiteContent;
import com.aigrowth.agent_backend.infrastructure.logger.DiagnosisLogger;
import com.aigrowth.agent_backend.infrastructure.scraper.SiteScraperService;
import com.aigrowth.agent_backend.infrastructure.store.DiagnosisContextStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartDiagnosisUseCase {

    private final SiteScraperService scraperService;
    private final DiagnosisContextStore contextStore;
    private final DiagnosisLogger diagnosisLogger;

    public DiagnosisStartResponse execute(DiagnosisStartRequest request) {
        String diagnosisId = UUID.randomUUID().toString();

        log.info("Starting diagnosis {} for domain: {}", diagnosisId, request.getDomain());

        // Scrape the website
        SiteContent siteContent = scraperService.scrape(request.getDomain());
        siteContent.setLanguage(request.getLanguage() != null ? request.getLanguage() : "pt");

        // Override brand name if the user provided one (highest priority)
        if (request.getClientBrandName() != null && !request.getClientBrandName().isBlank()) {
            siteContent.setBrandName(request.getClientBrandName().trim());
        }

        // Log step 1
        diagnosisLogger.logScrape(diagnosisId, request, siteContent);

        // Store context for the next step
        contextStore.save(diagnosisId, siteContent);

        String message = siteContent.isScraped()
                ? "Site analisado com sucesso. Agora vamos gerar as queries."
                : "Não conseguimos acessar o site, mas continuaremos com o domínio como contexto.";

        return DiagnosisStartResponse.builder()
                .diagnosisId(diagnosisId)
                .status(DiagnosisStatus.PENDING.name())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}