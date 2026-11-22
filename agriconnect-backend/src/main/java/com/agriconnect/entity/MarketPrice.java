package com.agriconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One official mandi (market) quotation for a commodity, imported from the AGMARKNET dataset
 * published on data.gov.in.
 *
 * <p>This is deliberately a separate concept from {@code Crop.pricePerUnit}. A listing price is
 * what one seller asks for their own stock; a market price is an independent reference rate for
 * a commodity in a given market on a given reporting date. Nothing links the two, so editing a
 * listing never moves the market rate and vice versa.
 *
 * <p>Only rows whose {@link #source} equals {@link #API_SOURCE} are ever queried. This keeps any
 * pre-existing manually seeded rows in the table (so no historical data is silently destroyed)
 * while guaranteeing the UI never displays them as real market data.
 */
@Entity
@Table(name = "market_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketPrice {

    /** Marks rows that came from the real government dataset; legacy/dummy rows have a null source. */
    public static final String API_SOURCE = "AGMARKNET/data.gov.in";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Commodity name as published by the source. Mirrored into {@link #cropName} for compatibility. */
    @Column(name = "commodity", length = 150)
    private String commodity;

    @Column(name = "crop_name", nullable = false, length = 100)
    private String cropName;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "district", length = 100)
    private String district;

    /** The market / mandi this rate was observed at, e.g. "Pune Market Yard". */
    @Column(name = "market_name", nullable = false, length = 150)
    private String marketName;

    @Column(name = "variety", length = 100)
    private String variety;

    @Column(name = "grade", length = 100)
    private String grade;

    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 12, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "modal_price", precision = 12, scale = 2)
    private BigDecimal modalPrice;

    /**
     * Rate for one {@link #unit}. For API rows this always mirrors {@link #modalPrice}; the field is
     * kept so older reads and exports continue to work without change.
     */
    @Column(name = "price_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String unit = "Quintal";

    /** The date the price was reported by the mandi, not the date we imported it. */
    @Column(name = "reporting_date")
    private LocalDate reportingDate;

    /** Provenance. Null means "not from the live feed" and is never returned by the service. */
    @Column(name = "source", length = 100)
    private String source;

    /** When this row was last fetched/refreshed from the government API. */
    @Column(name = "fetched_at")
    private LocalDateTime fetchedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (unit == null || unit.isBlank()) {
            unit = "Quintal";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
