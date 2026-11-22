package com.agriconnect.repository;

import com.agriconnect.entity.Crop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface CropRepository extends JpaRepository<Crop, Long> {
    List<Crop> findByFarmerId(Long farmerId);
    Optional<Crop> findByIdAndFarmerId(Long id, Long farmerId);
    List<Crop> findByCollectedFarmerId(Long collectedFarmerId);
    Optional<Crop> findByIdAndCollectedFarmerId(Long id, Long collectedFarmerId);
    List<Crop> findByAvailableTrue();
    Optional<Crop> findByIdAndAvailableTrue(Long id);

    /**
     * Row-locking read used while committing a verified payment. Two buyers paying for the last
     * of a listing at the same moment must not both decrement it, so the stock check and the
     * decrement happen under a {@code SELECT ... FOR UPDATE} lock. Must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Crop c WHERE c.id = :id")
    Optional<Crop> findByIdForUpdate(@Param("id") Long id);
}
