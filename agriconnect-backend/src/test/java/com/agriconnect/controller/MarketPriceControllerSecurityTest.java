package com.agriconnect.controller;

import com.agriconnect.config.SecurityConfig;
import com.agriconnect.dto.MandiSyncStatus;
import com.agriconnect.dto.MarketPriceResponse;
import com.agriconnect.security.CustomUserDetailsService;
import com.agriconnect.security.JwtAuthenticationFilter;
import com.agriconnect.service.MarketPriceService;
import com.agriconnect.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Market prices are read-only shared reference data; only marketplace roles may read them. */
@WebMvcTest(MarketPriceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MarketPriceControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private MarketPriceService marketPriceService;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private CustomUserDetailsService userDetailsService;

    private MarketPriceResponse sample() {
        return MarketPriceResponse.builder().id(1L).commodity("Wheat").cropName("Wheat")
                .pricePerUnit(new java.math.BigDecimal("2450"))
                .unit("Quintal").marketName("Pune Market Yard").build();
    }

    @Test
    @WithMockUser(authorities = "ROLE_FARMER")
    void farmerCanReadMarketPrices() throws Exception {
        when(marketPriceService.getAll(isNull(), isNull())).thenReturn(List.of(sample()));
        mvc.perform(get("/api/marketplace/market-prices")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_BUYER")
    void buyerCanReadMarketPrices() throws Exception {
        when(marketPriceService.getAll(eq("Pune"), isNull())).thenReturn(List.of(sample()));
        mvc.perform(get("/api/marketplace/market-prices").param("search", "Pune"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanReadMarketPrices() throws Exception {
        when(marketPriceService.getAll(isNull(), isNull())).thenReturn(List.of());
        mvc.perform(get("/api/marketplace/market-prices")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "farmer@example.com", authorities = "ROLE_FARMER")
    void farmerCanRequestTheirOwnStatePrices() throws Exception {
        when(marketPriceService.getForUser(eq("farmer@example.com"), isNull()))
                .thenReturn(List.of(sample()));
        mvc.perform(get("/api/marketplace/market-prices/for-me")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_FARMER")
    void farmerCanReadAvailableStates() throws Exception {
        when(marketPriceService.getStates()).thenReturn(List.of("Maharashtra"));
        mvc.perform(get("/api/marketplace/market-prices/states")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_FARMER")
    void farmerCanReadSyncStatus() throws Exception {
        when(marketPriceService.getStatus()).thenReturn(MandiSyncStatus.builder().build());
        mvc.perform(get("/api/marketplace/market-prices/status")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_MIDDLEMAN")
    void coordinatorCannotReadMarketPrices() throws Exception {
        mvc.perform(get("/api/marketplace/market-prices")).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketplace/market-prices/for-me")).andExpect(status().isForbidden());
        mvc.perform(get("/api/marketplace/market-prices/status")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void manualMarketPriceWriteEndpointNoLongerExists() throws Exception {
        // Prices can only be written by the scheduled importer. An old admin client posting here
        // must not be able to inject a hand-entered rate.
        mvc.perform(post("/api/admin/market-prices")
                        .contentType("application/json")
                        .content("{\"cropName\":\"Wheat\"}"))
                // No handler exists any more. The app's global exception mapper turns the
                // resulting NoResourceFoundException into a 500, so assert only that it is
                // never a successful write.
                .andExpect(result -> assertTrue(result.getResponse().getStatus() >= 400));
    }
}
