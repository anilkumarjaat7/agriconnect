package com.agriconnect.service;

import com.agriconnect.dto.MandiRecord;
import com.agriconnect.entity.MarketPrice;
import com.agriconnect.repository.MarketPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Imports official mandi quotations into {@code market_prices}.
 *
 * <p>The whole run is one transaction: if the feed fails midway, nothing is half-written. Rows are
 * matched on their natural key (state, district, mandi, commodity, variety, grade, reporting date)
 * so reruns refresh rather than duplicate. Old rows are kept with their original reporting date and
 * are surfaced as stale by the read service - they are never relabelled as today's prices.
 */
@Service
public class MandiPriceSyncService {

    private static final Logger log = LoggerFactory.getLogger(MandiPriceSyncService.class);
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE
    };
    private static final String NOT_SPECIFIED = "NA";

    private final MandiApiClient apiClient;
    private final MarketPriceRepository marketPriceRepository;
    private final MandiSyncStatusTracker statusTracker;
    private final String apiKey;

    public MandiPriceSyncService(MandiApiClient apiClient,
                                 MarketPriceRepository marketPriceRepository,
                                 MandiSyncStatusTracker statusTracker,
                                 @Value("${agriconnect.mandi.api-key:}") String apiKey) {
        this.apiClient = apiClient;
        this.marketPriceRepository = marketPriceRepository;
        this.statusTracker = statusTracker;
        this.apiKey = apiKey;
    }

    /** Runs one bounded import attempt. Failures are recorded and swallowed, never thrown at the scheduler. */
    @Transactional
    public synchronized void sync() {
        if (apiKey == null || apiKey.isBlank()) {
            statusTracker.recordSkipped("MANDI_API_KEY is not configured; no prices were fetched.");
            return;
        }

        try {
            MandiFetchResult result = apiClient.fetchAll();
            int written = 0;
            LocalDate newest = null;

            for (MandiRecord record : result.records()) {
                Optional<ParsedRecord> parsed = parse(record);
                if (parsed.isEmpty()) {
                    continue;
                }
                ParsedRecord price = parsed.get();
                upsert(price);
                written++;
                if (newest == null || price.reportingDate().isAfter(newest)) {
                    newest = price.reportingDate();
                }
            }

            statusTracker.recordSuccess(result.records().size(), written, newest);
            log.info("Mandi price sync finished: {} record(s) fetched, {} valid record(s) stored",
                    result.records().size(), written);
        } catch (Exception e) {
            // A failure part-way through the loop must not commit the rows written before it.
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (NoTransactionException ignored) {
                // Expected in plain unit tests without a Spring transaction.
            }
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            statusTracker.recordFailure(message);
            log.error("Mandi price sync failed: {}", message, e);
        }
    }

    private void upsert(ParsedRecord parsed) {
        Optional<MarketPrice> existing = marketPriceRepository.findByNaturalKey(
                MarketPrice.API_SOURCE,
                parsed.state(), parsed.district(), parsed.market(), parsed.commodity(),
                parsed.variety(), parsed.grade(), parsed.reportingDate());

        MarketPrice price = existing.orElseGet(() -> MarketPrice.builder()
                .state(parsed.state())
                .district(parsed.district())
                .marketName(parsed.market())
                .commodity(parsed.commodity())
                .cropName(parsed.commodity())
                .variety(parsed.variety())
                .grade(parsed.grade())
                .reportingDate(parsed.reportingDate())
                .source(MarketPrice.API_SOURCE)
                .unit("Quintal")
                .build());

        price.setMinPrice(parsed.minPrice());
        price.setMaxPrice(parsed.maxPrice());
        price.setModalPrice(parsed.modalPrice());
        price.setPricePerUnit(parsed.modalPrice());
        price.setSource(MarketPrice.API_SOURCE);
        price.setFetchedAt(java.time.LocalDateTime.now());

        marketPriceRepository.save(price);
    }

    /** Converts one raw record, returning empty for any row that cannot be trusted. */
    private Optional<ParsedRecord> parse(MandiRecord record) {
        String state = clean(record.getState());
        String district = clean(record.getDistrict());
        String market = clean(record.getMarket());
        String commodity = clean(record.getCommodity());
        LocalDate reportingDate = parseDate(record.getArrivalDate());

        if (state == null || district == null || market == null || commodity == null || reportingDate == null) {
            return nullAsEmpty();
        }
        if (reportingDate.isAfter(LocalDate.now().plusDays(1))) {
            return nullAsEmpty();
        }

        BigDecimal modalPrice = parsePrice(record.getModalPrice());
        if (modalPrice == null || modalPrice.signum() <= 0) {
            return nullAsEmpty();
        }
        BigDecimal minPrice = parsePrice(record.getMinPrice());
        BigDecimal maxPrice = parsePrice(record.getMaxPrice());

        return Optional.of(new ParsedRecord(
                state, district, market, commodity,
                cleanOrDefault(record.getVariety(), NOT_SPECIFIED),
                cleanOrDefault(record.getGrade(), NOT_SPECIFIED),
                reportingDate, minPrice, maxPrice, modalPrice));
    }

    private Optional<ParsedRecord> nullAsEmpty() {
        return Optional.empty();
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim().replaceAll("\\s+", " ");
        return trimmed.equalsIgnoreCase("NA") || trimmed.equals("--") || trimmed.equals("---")
                ? null : trimmed;
    }

    private String cleanOrDefault(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned == null ? fallback : cleaned;
    }

    private BigDecimal parsePrice(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        try {
            return new BigDecimal(cleaned.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(cleaned, formatter);
            } catch (DateTimeParseException ignored) {
                // try the next accepted layout
            }
        }
        return null;
    }

    private record ParsedRecord(String state, String district, String market, String commodity,
                                String variety, String grade, LocalDate reportingDate,
                                BigDecimal minPrice, BigDecimal maxPrice, BigDecimal modalPrice) {
    }
}
