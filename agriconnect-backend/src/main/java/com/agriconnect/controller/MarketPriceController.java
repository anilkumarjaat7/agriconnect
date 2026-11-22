package com.agriconnect.controller;

import com.agriconnect.dto.MandiSyncStatus;
import com.agriconnect.dto.MarketPriceResponse;
import com.agriconnect.service.MarketPriceService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Read-only access to official AGMARKNET mandi reference prices.
 *
 * <p>All routes sit under {@code /api/marketplace/**, already restricted by SecurityConfig to
 * BUYER, FARMER and ADMIN. There are no write routes: prices can only be inserted by the
 * scheduled importer, so no user can create a hand-entered "reference" price.
 */
@RestController
public class MarketPriceController {

    private final MarketPriceService marketPriceService;

    public MarketPriceController(MarketPriceService marketPriceService) {
        this.marketPriceService = marketPriceService;
    }

    /** All imported prices, with optional state and free-text filters. */
    @GetMapping("/api/marketplace/market-prices")
    public List<MarketPriceResponse> getPrices(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String state) {
        return marketPriceService.getAll(search, state);
    }

    /** State-wise prices for the signed-in user's registered state. */
    @GetMapping("/api/marketplace/market-prices/for-me")
    public List<MarketPriceResponse> getPricesForMe(
            @RequestParam(required = false) String search, Principal principal) {
        return marketPriceService.getForUser(principal.getName(), search);
    }

    @GetMapping("/api/marketplace/market-prices/states")
    public List<String> getStates() {
        return marketPriceService.getStates();
    }

    @GetMapping("/api/marketplace/market-prices/status")
    public MandiSyncStatus getStatus() {
        return marketPriceService.getStatus();
    }
}
