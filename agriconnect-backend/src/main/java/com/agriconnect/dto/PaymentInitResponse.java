package com.agriconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Everything the browser needs to open Razorpay Checkout. Deliberately excludes the key
 * secret; the publishable {@code keyId} is the only credential that may reach the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitResponse {
    /** AgriConnect order id, used again when verifying the result. */
    private Long orderId;
    private String orderNumber;
    /** Razorpay order id returned by the gateway. */
    private String razorpayOrderId;
    private BigDecimal amount;
    /** Amount in paise, as Razorpay Checkout expects it. */
    private long amountPaise;
    private String currency;
    private String keyId;
    private String buyerName;
    private String buyerEmail;
    private String buyerContact;
}
