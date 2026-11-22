package com.agriconnect.service;

import com.agriconnect.dto.MandiSyncStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * In-memory record of the most recent scheduled import attempt. Only the scheduler writes to it;
 * reads are safe from any thread. It is deliberately not persisted - a restart simply shows
 * "NEVER_RUN" until the next successful import, which is honest about what the process knows.
 */
@Component
public class MandiSyncStatusTracker {

    private volatile LocalDateTime lastSyncAt;
    private volatile String lastSyncStatus = "NEVER_RUN";
    private volatile String lastSyncMessage;

    public void recordSuccess(int recordsProcessed, int recordsWritten, LocalDate newestReportingDate) {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncStatus = "SUCCESS";
        this.lastSyncMessage = String.format(
                "Imported %d record(s), stored %d record(s): %s",
                recordsProcessed, recordsWritten,
                newestReportingDate == null ? "no reporting date supplied" : newestReportingDate.toString());
    }

    public void recordFailure(String message) {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncStatus = "FAILED";
        this.lastSyncMessage = truncate(message);
    }

    public void recordSkipped(String message) {
        this.lastSyncAt = LocalDateTime.now();
        this.lastSyncStatus = "SKIPPED";
        this.lastSyncMessage = truncate(message);
    }

    public MandiSyncStatus snapshot(boolean enabled, long importedRecords,
                                    LocalDate newestReportingDate, int staleDays) {
        return MandiSyncStatus.builder()
                .enabled(enabled)
                .lastSyncAt(lastSyncAt)
                .lastSyncStatus(lastSyncStatus)
                .lastSyncMessage(lastSyncMessage)
                .importedRecords(importedRecords)
                .newestReportingDate(newestReportingDate)
                .staleDays(staleDays)
                .build();
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
