package com.agriconnect.config;

import com.agriconnect.service.MandiPriceSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional first import at startup, off by default. Enable with {@code MANDI_INITIAL_SYNC=true}
 * once a valid {@code MANDI_API_KEY} is configured to populate an empty table immediately.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MandiInitialSyncRunner implements ApplicationRunner {

    private final MandiPriceSyncService syncService;
    private final boolean initialSync;

    public MandiInitialSyncRunner(MandiPriceSyncService syncService,
                                  @Value("${agriconnect.mandi.initial-sync:false}") boolean initialSync) {
        this.syncService = syncService;
        this.initialSync = initialSync;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (initialSync) {
            syncService.sync();
        }
    }
}
