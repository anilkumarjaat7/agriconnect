package com.agriconnect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** Raw envelope returned by the data.gov.in Data API. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MandiApiResponse {

    private String status;
    private String message;
    private Integer total;
    private Integer count;
    private String limit;
    private String offset;

    @JsonProperty("records")
    private List<MandiRecord> records;
}
