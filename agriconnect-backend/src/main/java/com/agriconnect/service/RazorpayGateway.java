package com.agriconnect.service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Thin boundary around Razorpay so the payment flow can be unit-tested without network access.
 *
 * <p>The implementation is the only place that knows the API key secret; callers only ever see
 * the publishable key id and a verified/unverified boolean.
 */
public interface RazorpayGateway {

    /** True when both the key id and the key secret are configured. */
    boolean isConfigured();

    /** Publishable key id, safe to hand to the browser. */
    String getKeyId();

    String getCurrency();

    /**
     * Creates a Razorpay order. The returned id is passed to Checkout, which echoes it back with
     * the payment so the signature can be verified.
     */
    CreatedOrder createOrder(BigDecimal amount, String receipt, Map<String, String> notes);

    /**
     * Verifies {@code HMAC_SHA256(razorpay_order_id + "|" + razorpay_payment_id, keySecret)}.
     * A false answer means the callback must not be trusted.
     */
    boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature);

    record CreatedOrder(String id, long amountPaise, String currency) {
    }
}
