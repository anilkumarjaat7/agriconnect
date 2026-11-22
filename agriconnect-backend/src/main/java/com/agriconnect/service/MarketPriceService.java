package com.agriconnect.service;

import com.agriconnect.dto.MandiSyncStatus;
import com.agriconnect.dto.MarketPriceResponse;

import java.util.List;

/**
 * Read access to official AGMARKNET mandi reference prices.
 *
 * <p>There are deliberately no create/update/delete operations: the only writer is the scheduled
 * importer, so neither an administrator nor an API caller can inject a hand-entered "reference"
 * price that would be presented as live government data.
 */
public interface MarketPriceService {

    /** All imported records, optionally narrowed by state and/or a free-text search term. */
    List<MarketPriceResponse> getAll(String search, String state);

    /** Records for the state on the signed-in farmer's profile. */
    List<MarketPriceResponse> getForUser(String email, String search);

    /** States currently present in the imported dataset. */
    List<String> getStates();

    /** Last scheduled import outcome and freshness metadata. */
    MandiSyncStatus getStatus();
}
