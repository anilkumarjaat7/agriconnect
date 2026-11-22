package com.agriconnect.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A Razorpay payment attempt for exactly one order.
 *
 * <p>Kept in its own table rather than as columns on {@link Order}: an order is the buyer's
 * purchase record and must survive independently of any gateway detail, while this row is the
 * auditable trail of what Razorpay told us (order id, payment id, signature, status).
 *
 * <p>{@code order_id} is unique and {@code razorpay_order_id} is unique, so a retried callback
 * cannot create a second payment row for the same checkout. The status is advanced only by
 * {@code PaymentServiceImpl}; it is the single source of truth for whether stock has been
 * consumed.
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /** Checkout started, no money confirmed yet. */
    public static final String STATUS_CREATED = "CREATED";
    /** Signature verified; order fulfilled and stock deducted. */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** Gateway rejected the payment or the signature did not match. */
    public static final String STATUS_FAILED = "FAILED";
    /** Buyer closed the checkout or abandoned the attempt before paying. */
    public static final String STATUS_CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 64)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 64)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 255)
    private String razorpaySignature;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = STATUS_CREATED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
