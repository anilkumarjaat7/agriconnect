package com.agriconnect.service;

import com.agriconnect.dto.MandiSyncStatus;
import com.agriconnect.dto.MarketPriceResponse;
import com.agriconnect.entity.MarketPrice;
import com.agriconnect.entity.User;
import com.agriconnect.repository.MarketPriceRepository;
import com.agriconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketPriceServiceImplTest {

    @Mock
    private MarketPriceRepository marketPriceRepository;
    @Mock
    private UserRepository userRepository;

    private MandiSyncStatusTracker tracker;
    private MarketPriceServiceImpl service;

    @BeforeEach
    void setUp() {
        tracker = new MandiSyncStatusTracker();
        service = new MarketPriceServiceImpl(
                marketPriceRepository, userRepository, tracker, "test-api-key", 7);
    }

    private MarketPrice official(String state, String commodity, String reportingDate) {
        return MarketPrice.builder()
                .id(1L)
                .state(state)
                .district("Pune")
                .marketName("Pune Market Yard")
                .commodity(commodity)
                .cropName(commodity)
                .variety("Local")
                .grade("FAQ")
                .minPrice(new BigDecimal("2200"))
                .maxPrice(new BigDecimal("2500"))
                .modalPrice(new BigDecimal("2400"))
                .pricePerUnit(new BigDecimal("2400"))
                .unit("Quintal")
                .reportingDate(LocalDate.parse(reportingDate))
                .source(MarketPrice.API_SOURCE)
                .build();
    }

    @Test
    void readsArePinnedToTheOfficialSourceAndPassFiltersThrough() {
        when(marketPriceRepository.searchOfficialPrices(
                eq(MarketPrice.API_SOURCE), eq("Maharashtra"), eq("Wheat")))
                .thenReturn(List.of(official("Maharashtra", "Wheat", "2026-11-17")));

        List<MarketPriceResponse> prices = service.getAll(" Wheat ", " Maharashtra ");

        assertEquals(1, prices.size());
        assertEquals("Wheat", prices.get(0).getCommodity());
        assertEquals(MarketPrice.API_SOURCE, prices.get(0).getSource());
    }

    @Test
    void blankSearchAndStateAreNormalisedToNoFilter() {
        when(marketPriceRepository.searchOfficialPrices(MarketPrice.API_SOURCE, null, null))
                .thenReturn(List.of());

        assertTrue(service.getAll("  ", " ").isEmpty());
        verify(marketPriceRepository).searchOfficialPrices(MarketPrice.API_SOURCE, null, null);
    }

    @Test
    void farmerReadsPricesForTheStateOnTheirProfile() {
        User farmer = new User();
        farmer.setState("Maharashtra");
        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(farmer));
        when(marketPriceRepository.searchOfficialPrices(MarketPrice.API_SOURCE, "Maharashtra", null))
                .thenReturn(List.of(official("Maharashtra", "Wheat", "2026-11-17")));

        List<MarketPriceResponse> prices = service.getForUser("farmer@example.com", null);

        assertEquals(1, prices.size());
        assertEquals("Maharashtra", prices.get(0).getState());
    }

    @Test
    void farmerWithoutARegisteredStateGetsNoPricesRatherThanAllStates() {
        User farmer = new User();
        farmer.setState("  ");
        when(userRepository.findByEmail("farmer@example.com")).thenReturn(Optional.of(farmer));

        assertTrue(service.getForUser("farmer@example.com", null).isEmpty());
        verify(marketPriceRepository, never()).searchOfficialPrices(anyString(), anyString(), anyString());
    }

    @Test
    void modalPriceIsMirroredIntoTheCompatibilityField() {
        when(marketPriceRepository.searchOfficialPrices(MarketPrice.API_SOURCE, null, null))
                .thenReturn(List.of(official("Maharashtra", "Wheat", "2026-11-17")));

        MarketPriceResponse price = service.getAll(null, null).get(0);

        assertEquals(new BigDecimal("2400"), price.getModalPrice());
        assertEquals(price.getModalPrice(), price.getPricePerUnit());
        assertEquals("Quintal", price.getUnit());
    }

    @Test
    void recentReportingDateIsNotMarkedStale() {
        when(marketPriceRepository.searchOfficialPrices(MarketPrice.API_SOURCE, null, null))
                .thenReturn(List.of(official("Maharashtra", "Wheat", LocalDate.now().toString())));

        assertFalse(service.getAll(null, null).get(0).isStale());
    }

    @Test
    void oldReportingDateIsMarkedStaleRatherThanDisplayedAsLive() {
        when(marketPriceRepository.searchOfficialPrices(MarketPrice.API_SOURCE, null, null))
                .thenReturn(List.of(official("Maharashtra", "Wheat",
                        LocalDate.now().minusDays(30).toString())));

        assertTrue(service.getAll(null, null).get(0).isStale());
    }

    @Test
    void statusReportsConfigurationAndStoredRecordCount() {
        when(marketPriceRepository.countBySource(MarketPrice.API_SOURCE)).thenReturn(42L);
        when(marketPriceRepository.findLatestReportingDate(MarketPrice.API_SOURCE))
                .thenReturn(Optional.of(LocalDate.of(2026, 11, 17)));
        tracker.recordSuccess(100, 42, LocalDate.of(2026, 11, 17));

        MandiSyncStatus status = service.getStatus();

        assertTrue(status.isEnabled());
        assertEquals(42L, status.getImportedRecords());
        assertEquals("SUCCESS", status.getLastSyncStatus());
        assertEquals(LocalDate.of(2026, 11, 17), status.getNewestReportingDate());
        assertEquals(7, status.getStaleDays());
    }

    @Test
    void statusReportsDisabledWhenNoApiKeyIsConfigured() {
        service = new MarketPriceServiceImpl(
                marketPriceRepository, userRepository, tracker, "", 7);
        when(marketPriceRepository.countBySource(MarketPrice.API_SOURCE)).thenReturn(0L);
        when(marketPriceRepository.findLatestReportingDate(MarketPrice.API_SOURCE))
                .thenReturn(Optional.empty());

        assertFalse(service.getStatus().isEnabled());
    }
}
