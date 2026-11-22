package com.agriconnect.repository;

import com.agriconnect.entity.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    /**
     * Every query in this repository pins the source. Rows with a null source (the old manually
     * seeded/dummy records) remain in the table for data-safety reasons but are never returned.
     */
    @Query("""
            SELECT p FROM MarketPrice p
            WHERE p.source = :source
              AND (:state IS NULL OR :state = '' OR LOWER(p.state) = LOWER(:state))
              AND (:search IS NULL OR :search = '' OR
                   LOWER(p.commodity) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(p.cropName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(p.marketName) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(p.district) LIKE LOWER(CONCAT('%', :search, '%')) OR
                   LOWER(p.state) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY p.state ASC, p.commodity ASC, p.marketName ASC, p.reportingDate DESC, p.id DESC
            """)
    List<MarketPrice> searchOfficialPrices(@Param("source") String source,
                                            @Param("state") String state,
                                            @Param("search") String search);

    /** Distinct states present in the imported data, for the state filter dropdown. */
    @Query("SELECT DISTINCT p.state FROM MarketPrice p WHERE p.source = :source AND p.state IS NOT NULL ORDER BY p.state")
    List<String> findStatesBySource(@Param("source") String source);

    /** Natural key used by the scheduler to make repeated imports idempotent. */
    @Query("""
            SELECT p FROM MarketPrice p
            WHERE p.source = :source
              AND LOWER(p.state) = LOWER(:state)
              AND LOWER(p.district) = LOWER(:district)
              AND LOWER(p.marketName) = LOWER(:market)
              AND LOWER(p.commodity) = LOWER(:commodity)
              AND LOWER(p.variety) = LOWER(:variety)
              AND LOWER(p.grade) = LOWER(:grade)
              AND p.reportingDate = :reportingDate
            """)
    Optional<MarketPrice> findByNaturalKey(@Param("source") String source,
                                           @Param("state") String state,
                                           @Param("district") String district,
                                           @Param("market") String market,
                                           @Param("commodity") String commodity,
                                           @Param("variety") String variety,
                                           @Param("grade") String grade,
                                           @Param("reportingDate") LocalDate reportingDate);

    long countBySource(String source);

    @Query("SELECT MAX(p.reportingDate) FROM MarketPrice p WHERE p.source = :source")
    Optional<LocalDate> findLatestReportingDate(@Param("source") String source);
}
