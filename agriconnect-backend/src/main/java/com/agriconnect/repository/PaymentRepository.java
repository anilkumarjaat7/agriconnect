package com.agriconnect.repository;

import com.agriconnect.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    /**
     * Pessimistic lock taken while verifying a payment. Two Razorpay callbacks for the same
     * checkout (browser handler plus a retry) are serialised here, so the status check that
     * guards stock deduction can never be observed by both at once. Must run in a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.razorpayOrderId = :razorpayOrderId")
    Optional<Payment> findWithLockByRazorpayOrderId(@Param("razorpayOrderId") String razorpayOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    Optional<Payment> findWithLockByOrderId(@Param("orderId") Long orderId);
}
