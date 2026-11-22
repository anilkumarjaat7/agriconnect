package com.agriconnect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * One raw AGMARKNET record. Prices stay as strings here because the feed occasionally contains
 * placeholders; validation and numeric conversion happen in the importer, where a bad row can be
 * skipped without discarding the rest of the page.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MandiRecord {

    private String state;
    private String district;
    private String market;

    private String commodity;
    private String variety;
    private String grade;

    @JsonProperty("arrival_date")
    private String arrivalDate;

    @JsonProperty("min_price")
    private String minPrice;

    @JsonProperty("max_price")
    private String maxPrice;

    @JsonProperty("modal_price")
    private String modalPrice;
}
