package com.agriconnect.config;

import com.agriconnect.service.MandiPriceSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily refresh of the official mandi dataset. Times are explicit so server locale cannot shift it. */
@Component
public class MandiPriceScheduler {

    private final MandiPriceSyncService syncService;

    public MandiPriceScheduler(MandiPriceSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "${agriconnect.mandi.sync-cron:0 0 6 * * *}", zone = "Asia/Kolkata")
    public void refreshMandiPrices() {
        syncService.sync();
    }
}
