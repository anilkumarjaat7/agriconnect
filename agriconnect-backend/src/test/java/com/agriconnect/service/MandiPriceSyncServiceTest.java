package com.agriconnect.service;

import com.agriconnect.dto.MandiRecord;
import com.agriconnect.entity.MarketPrice;
import com.agriconnect.repository.MarketPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class MandiPriceSyncServiceTest {

    @Mock
    private MandiApiClient apiClient;
    @Mock
    private MarketPriceRepository marketPriceRepository;

    private MandiSyncStatusTracker tracker;
    private MandiPriceSyncService service;

    @BeforeEach
    void setUp() {
        tracker = new MandiSyncStatusTracker();
        service = new MandiPriceSyncService(apiClient, marketPriceRepository, tracker, "test-key");
    }

    private MandiRecord validRecord() {
        MandiRecord record = new MandiRecord();
        record.setState(" Maharashtra ");
        record.setDistrict("Pune");
        record.setMarket("Pune Market Yard");
        record.setCommodity("Wheat");
        record.setVariety("  ");
        record.setGrade("FAQ");
        record.setArrivalDate("17/11/2026");
        record.setMinPrice("2200");
        record.setMaxPrice("2500");
        record.setModalPrice("2400");
        return record;
    }

    @Test
    void blankApiKeySkipsTheFetchEntirely() {
        MandiPriceSyncService disabled =
                new MandiPriceSyncService(apiClient, marketPriceRepository, tracker, "  ");

        disabled.sync();

        verifyNoInteractions(apiClient, marketPriceRepository);
        assertEquals("SKIPPED", tracker.snapshot(true, 0, null, 7).getLastSyncStatus());
    }

    @Test
    void validRecordIsStoredWithSourceAndNormalisedFields() {
        when(apiClient.fetchAll()).thenReturn(new MandiFetchResult(List.of(validRecord()), 1));
        when(marketPriceRepository.findByNaturalKey(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(marketPriceRepository.save(any(MarketPrice.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.sync();

        ArgumentCaptor<MarketPrice> captor = ArgumentCaptor.forClass(MarketPrice.class);
        verify(marketPriceRepository).save(captor.capture());
        MarketPrice saved = captor.getValue();
        assertEquals("Maharashtra", saved.getState());
        assertEquals("Pune Market Yard", saved.getMarketName());
        assertEquals("Wheat", saved.getCommodity());
        assertEquals("Wheat", saved.getCropName());
        assertEquals("NA", saved.getVariety());
        assertEquals(new BigDecimal("2400"), saved.getModalPrice());
        assertEquals(saved.getModalPrice(), saved.getPricePerUnit());
        assertEquals(MarketPrice.API_SOURCE, saved.getSource());
        assertEquals(LocalDate.of(2026, 11, 17), saved.getReportingDate());
        assertEquals("SUCCESS", tracker.snapshot(true, 0, null, 7).getLastSyncStatus());
    }

    @Test
    void repeatImportUpdatesTheExistingRowInsteadOfCreatingADuplicate() {
        MarketPrice existing = MarketPrice.builder()
                .id(9L).state("Maharashtra").district("Pune").marketName("Pune Market Yard")
                .commodity("Wheat").cropName("Wheat").variety("NA").grade("FAQ")
                .reportingDate(LocalDate.of(2026, 11, 17))
                .source(MarketPrice.API_SOURCE).unit("Quintal").build();
        when(apiClient.fetchAll()).thenReturn(new MandiFetchResult(List.of(validRecord()), 1));
        when(marketPriceRepository.findByNaturalKey(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(marketPriceRepository.save(any(MarketPrice.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.sync();

        ArgumentCaptor<MarketPrice> captor = ArgumentCaptor.forClass(MarketPrice.class);
        verify(marketPriceRepository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(9L, captor.getValue().getId());
        assertEquals(new BigDecimal("2400"), captor.getValue().getModalPrice());
        assertNotNull(captor.getValue().getFetchedAt());
    }

    @Test
    void malformedRowsAreSkippedWithoutAbortingTheImport() {
        MandiRecord badDate = validRecord();
        badDate.setArrivalDate("not-a-date");
        MandiRecord badPrice = validRecord();
        badPrice.setModalPrice("NA");
        MandiRecord missingState = validRecord();
        missingState.setState(" ");

        when(apiClient.fetchAll()).thenReturn(
                new MandiFetchResult(List.of(badDate, badPrice, missingState, validRecord()), 1));
        when(marketPriceRepository.findByNaturalKey(anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(marketPriceRepository.save(any(MarketPrice.class)))
                .thenAnswer(call -> call.getArgument(0));

        service.sync();

        verify(marketPriceRepository, times(1)).save(any(MarketPrice.class));
    }

    @Test
    void apiFailureIsRecordedAndDoesNotWriteAnyRows() {
        when(apiClient.fetchAll()).thenThrow(new IllegalStateException("HTTP 503"));

        assertDoesNotThrow(() -> service.sync());

        verify(marketPriceRepository, never()).save(any(MarketPrice.class));
        assertEquals("FAILED", tracker.snapshot(true, 0, null, 7).getLastSyncStatus());
        assertEquals("HTTP 503", tracker.snapshot(true, 0, null, 7).getLastSyncMessage());
    }
}
