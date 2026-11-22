package com.agriconnect.service;

import com.agriconnect.dto.MandiApiResponse;
import com.agriconnect.dto.MandiRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP client for the AGMARKNET "Current Daily Price of Various Commodities from Various
 * Markets (Mandi)" dataset on data.gov.in.
 *
 * <p>The API key is injected from {@code MANDI_API_KEY} and never leaves the backend. Requests use
 * a bounded number of pages so a malformed {@code total} response cannot cause an endless loop.
 */
@Component
public class MandiApiClient {

    private static final Logger log = LoggerFactory.getLogger(MandiApiClient.class);

    private final org.springframework.web.client.RestTemplate restTemplate;
    private final String baseUrl;
    private final String resourceId;
    private final String apiKey;
    private final int pageSize;
    private final int maxPages;

    public MandiApiClient(RestTemplateBuilder restTemplateBuilder,
                          @Value("${agriconnect.mandi.base-url:https://api.data.gov.in/resource}") String baseUrl,
                          @Value("${agriconnect.mandi.resource-id:}") String resourceId,
                          @Value("${agriconnect.mandi.api-key:}") String apiKey,
                          @Value("${agriconnect.mandi.page-size:1000}") int pageSize,
                          @Value("${agriconnect.mandi.max-pages:20}") int maxPages,
                          @Value("${agriconnect.mandi.connect-timeout-ms:5000}") long connectTimeoutMs,
                          @Value("${agriconnect.mandi.read-timeout-ms:20000}") long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.baseUrl = baseUrl;
        this.resourceId = resourceId;
        this.apiKey = apiKey;
        this.pageSize = Math.max(1, pageSize);
        this.maxPages = Math.max(1, maxPages);
    }

    /** Fetches every available page. Throws on transport or API errors so the caller can report them. */
    public MandiFetchResult fetchAll() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("MANDI_API_KEY is not configured on the server.");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalStateException("MANDI_RESOURCE_ID is not configured on the server.");
        }

        List<MandiRecord> records = new ArrayList<>();
        int offset = 0;
        int pages = 0;

        for (int page = 0; page < maxPages; page++) {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .pathSegment(resourceId)
                    .queryParam("api-key", apiKey)
                    .queryParam("format", "json")
                    .queryParam("limit", pageSize)
                    .queryParam("offset", offset)
                    .build()
                    .encode()
                    .toUri();

            MandiApiResponse body = restTemplate.getForObject(uri, MandiApiResponse.class);
            if (body == null) {
                throw new IllegalStateException("Empty response from the mandi price API.");
            }
            if (!"ok".equalsIgnoreCase(body.getStatus()) && body.getRecords() == null) {
                throw new IllegalStateException("Mandi price API error: "
                        + (body.getMessage() == null ? body.getStatus() : body.getMessage()));
            }

            List<MandiRecord> pageRecords = body.getRecords();
            pages++;
            if (pageRecords == null || pageRecords.isEmpty()) {
                break;
            }
            records.addAll(pageRecords);
            log.debug("Fetched {} mandi records at offset {}", pageRecords.size(), offset);

            int fetched = body.getCount() != null ? body.getCount() : pageRecords.size();
            int total = body.getTotal() != null ? body.getTotal() : Integer.MAX_VALUE;
            if (fetched < pageSize || offset + fetched >= total) {
                break;
            }
            offset += fetched;
        }

        return new MandiFetchResult(List.copyOf(records), pages);
    }
}
