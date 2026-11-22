package com.agriconnect.service;

import com.agriconnect.dto.MandiSyncStatus;
import com.agriconnect.dto.MarketPriceResponse;
import com.agriconnect.entity.MarketPrice;
import com.agriconnect.repository.MarketPriceRepository;
import com.agriconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketPriceServiceImpl implements MarketPriceService {

    private final MarketPriceRepository marketPriceRepository;
    private final UserRepository userRepository;
    private final MandiSyncStatusTracker syncStatusTracker;
    private final String apiKey;
    private final int staleDays;

    public MarketPriceServiceImpl(MarketPriceRepository marketPriceRepository,
                                  UserRepository userRepository,
                                  MandiSyncStatusTracker syncStatusTracker,
                                  @Value("${agriconnect.mandi.api-key:}") String apiKey,
                                  @Value("${agriconnect.mandi.stale-days:7}") int staleDays) {
        this.marketPriceRepository = marketPriceRepository;
        this.userRepository = userRepository;
        this.syncStatusTracker = syncStatusTracker;
        this.apiKey = apiKey;
        this.staleDays = staleDays;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketPriceResponse> getAll(String search, String state) {
        return marketPriceRepository
                .searchOfficialPrices(MarketPrice.API_SOURCE, clean(state), clean(search))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MarketPriceResponse> getForUser(String email, String search) {
        String state = userRepository.findByEmail(email)
                .map(user -> clean(user.getState()))
                .orElse(null);

        // A profile with no state cannot be matched to a state-wise mandi feed. Returning nothing
        // is honest; falling back to "all states" would silently show prices from unrelated states.
        if (state == null) {
            return List.of();
        }
        return getAll(search, state);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getStates() {
        return marketPriceRepository.findStatesBySource(MarketPrice.API_SOURCE);
    }

    @Override
    @Transactional(readOnly = true)
    public MandiSyncStatus getStatus() {
        long importedRecords = marketPriceRepository.countBySource(MarketPrice.API_SOURCE);
        LocalDate newestReportingDate = marketPriceRepository
                .findLatestReportingDate(MarketPrice.API_SOURCE)
                .orElse(null);
        return syncStatusTracker.snapshot(
                apiKey != null && !apiKey.isBlank(), importedRecords, newestReportingDate, staleDays);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private MarketPriceResponse toResponse(MarketPrice price) {
        LocalDate reportingDate = price.getReportingDate();
        boolean stale = reportingDate == null
                || reportingDate.isBefore(LocalDate.now().minusDays(staleDays));

        return MarketPriceResponse.builder()
                .id(price.getId())
                .commodity(price.getCommodity())
                .cropName(price.getCropName())
                .state(price.getState())
                .district(price.getDistrict())
                .variety(price.getVariety())
                .grade(price.getGrade())
                .marketName(price.getMarketName())
                .minPrice(price.getMinPrice())
                .maxPrice(price.getMaxPrice())
                .modalPrice(price.getModalPrice())
                .pricePerUnit(price.getModalPrice() != null ? price.getModalPrice() : price.getPricePerUnit())
                .unit(price.getUnit())
                .reportingDate(reportingDate)
                .source(price.getSource())
                .fetchedAt(price.getFetchedAt())
                .updatedAt(price.getUpdatedAt())
                .stale(stale)
                .build();
    }
}
