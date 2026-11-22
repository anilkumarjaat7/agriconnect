package com.agriconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * An official mandi quotation as shown to farmers and buyers. Carries no seller, buyer or account
 * reference: this is shared reference data, so there is nothing role-specific to withhold.
 *
 * <p>The {@code stale} flag is computed from the source reporting date. The UI uses it to avoid
 * presenting old records as "live" prices.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketPriceResponse {
    private Long id;

    /** Commodity name as published by the source (also exposed as {@link #cropName}). */
    private String commodity;
    private String cropName;
    private String state;
    private String district;
    private String variety;
    private String grade;

    /** Mandi name. */
    private String marketName;

    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal modalPrice;

    /** Mirrors {@link #modalPrice} for backwards compatibility with earlier clients. */
    private BigDecimal pricePerUnit;
    private String unit;

    /** Date the mandi reported this price. */
    private LocalDate reportingDate;

    /** Source of the record, always the government dataset identifier. */
    private String source;

    /** When this record was last fetched from the source. */
    private LocalDateTime fetchedAt;

    /** When this record was last changed locally. */
    private LocalDateTime updatedAt;

    /** True when the reporting date is older than the configured freshness window. */
    private boolean stale;
}
