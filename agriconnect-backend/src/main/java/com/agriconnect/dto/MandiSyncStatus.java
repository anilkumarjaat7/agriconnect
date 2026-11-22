package com.agriconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Observability for the automatic market-price import. Never contains price values itself. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MandiSyncStatus {
    private boolean enabled;
    private LocalDateTime lastSyncAt;
    private String lastSyncStatus;   // SUCCESS | FAILED | NEVER_RUN
    private String lastSyncMessage;
    private long importedRecords;
    private LocalDate newestReportingDate;
    private int staleDays;
}
