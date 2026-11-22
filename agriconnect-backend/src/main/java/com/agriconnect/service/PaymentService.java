package com.agriconnect.service;

import com.agriconnect.dto.CancelPaymentRequest;
import com.agriconnect.dto.PaymentInitRequest;
import com.agriconnect.dto.PaymentInitResponse;
import com.agriconnect.dto.PaymentResponse;
import com.agriconnect.dto.RazorpayConfigResponse;
import com.agriconnect.dto.VerifyPaymentRequest;

import java.util.Optional;

/**
 * Buyer-facing Razorpay checkout. Every endpoint is scoped to the signed-in buyer, and the key
 * secret never leaves this layer.
 */
public interface PaymentService {

    /** Publishable configuration for the frontend. {@code enabled=false} keeps the legacy flow. */
    RazorpayConfigResponse getConfig();

    /** Creates an unpaid order + Razorpay order. Stock and cart are untouched at this point. */
    PaymentInitResponse createCheckout(PaymentInitRequest request, String buyerEmail);

    /**
     * Verifies the Razorpay signature and, only on success, fulfils the order exactly once.
     * A repeated callback for an already successful payment is a no-op.
     */
    PaymentResponse verifyPayment(VerifyPaymentRequest request, String buyerEmail);

    /** Marks an unpaid attempt cancelled when the buyer closes the checkout. */
    PaymentResponse cancelPayment(CancelPaymentRequest request, String buyerEmail);

    Optional<PaymentResponse> getByOrder(Long orderId, String buyerEmail);
}
