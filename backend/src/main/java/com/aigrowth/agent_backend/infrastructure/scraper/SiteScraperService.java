package com.aigrowth.agent_backend.infrastructure.scraper;

import com.aigrowth.agent_backend.domain.model.SiteContent;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class SiteScraperService {

    private static final int TIMEOUT_MS = 8_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; AIGrowthBot/1.0; +https://aigrowth.com.br/bot)";

    public SiteContent scrape(String domain) {
        String baseUrl = normalizeUrl(domain);
        log.info("Starting scrape of: {}", baseUrl);

        Document doc = fetchDocument(baseUrl);
        String homeText = doc != null ? extractMeaningfulText(doc) : null;
        String brandName = doc != null ? extractBrandName(doc, domain) : extractBrandNameFromDomain(domain);

        log.info("Scrape finished for {}. home={}, brandName={}", domain,
                homeText != null ? "OK" : "FAIL", brandName);

        return SiteContent.builder()
                .domain(domain)
                .brandName(brandName)
                .homeText(homeText)
                .scraped(homeText != null)
                .build();
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(false)
                    .get();
        } catch (Exception e) {
            log.debug("Failed to access {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extracts the brand name from the page title.
     * Most websites put their brand name as the first segment of the title.
     * Example: "Nomad - Conta Global para Brasileiros" → "Nomad"
     * Example: "HubSpot | CRM Platform" → "HubSpot"
     */
    private String extractBrandName(Document doc, String domain) {
        String title = doc.title();
        if (title == null || title.isBlank()) {
            return extractBrandNameFromDomain(domain);
        }

        // Split by common title separators: " - ", " | ", " — ", " · ", " : "
        String[] parts = title.split("\\s*[-|—·:]\\s*");
        String candidate = parts[0].trim();

        // If the first part is too short (< 2 chars) or too long (> 30 chars), use domain fallback
        if (candidate.length() < 2 || candidate.length() > 30) {
            return extractBrandNameFromDomain(domain);
        }

        log.debug("Brand name extracted from title: '{}' → '{}'", title, candidate);
        return candidate;
    }

    /**
     * Fallback: extracts brand name from the domain itself.
     * Example: nomadglobal.com → "nomadglobal"
     * Example: www.hubspot.com → "hubspot"
     */
    private String extractBrandNameFromDomain(String domain) {
        return domain.toLowerCase()
                .replaceFirst("^(https?://)?", "")
                .replaceFirst("^www\\.", "")
                .replaceFirst("\\.[a-z]{2,}(\\.[a-z]{2,})?$", ""); // remove .com, .com.br, etc.
    }

    /**
     * Extracts meaningful text from HTML, removing nav, footer, scripts, etc.
     */
    private String extractMeaningfulText(Document doc) {
        // Remove noise
        doc.select("nav, footer, header, script, style, noscript, iframe, " +
                   "svg, img, [aria-hidden=true], .cookie-banner, #cookie").remove();

        // Try to extract main content first
        Element main = doc.selectFirst("main, article, [role=main], #content, .content, #main");
        Element target = (main != null) ? main : doc.body();

        if (target == null) return null;

        // Prioritize paragraphs and headings
        Elements meaningful = target.select("h1, h2, h3, p, li");
        if (meaningful.isEmpty()) {
            return target.text();
        }

        StringBuilder sb = new StringBuilder();
        for (Element el : meaningful) {
            String text = el.text().trim();
            if (text.length() > 20) { // skip very short text (labels, buttons)
                sb.append(text).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private String normalizeUrl(String domain) {
        domain = domain.trim().toLowerCase();
        if (!domain.startsWith("http://") && !domain.startsWith("https://")) {
            domain = "https://" + domain;
        }
        // Remove trailing slash
        return domain.replaceAll("/$", "");
    }
}
