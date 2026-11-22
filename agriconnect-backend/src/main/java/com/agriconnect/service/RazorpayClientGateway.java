package com.agriconnect.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Razorpay SDK backed implementation.
 *
 * <p>Credentials are injected from environment-backed properties (RAZORPAY_KEY_ID /
 * RAZORPAY_KEY_SECRET). When they are blank the gateway reports itself unconfigured and the
 * application deliberately falls back to the pre-existing no-payment order flow instead of
 * failing to start.
 */
@Service
public class RazorpayClientGateway implements RazorpayGateway {

    private static final Logger log = LoggerFactory.getLogger(RazorpayClientGateway.class);

    private final String keyId;
    private final String keySecret;
    private final String currency;

    private final Object clientLock = new Object();
    private RazorpayClient client;

    public RazorpayClientGateway(
            @Value("${agriconnect.razorpay.key-id:}") String keyId,
            @Value("${agriconnect.razorpay.key-secret:}") String keySecret,
            @Value("${agriconnect.razorpay.currency:INR}") String currency) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.currency = (currency == null || currency.isBlank()) ? "INR" : currency.trim();
    }

    @Override
    public boolean isConfigured() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public String getCurrency() {
        return currency;
    }

    @Override
    public CreatedOrder createOrder(BigDecimal amount, String receipt, Map<String, String> notes) {
        if (!isConfigured()) {
            throw new IllegalStateException("Razorpay credentials are not configured");
        }
        // Razorpay works in the smallest currency unit; for INR that is paise.
        long amountPaise = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();

        JSONObject request = new JSONObject();
        request.put("amount", amountPaise);
        request.put("currency", currency);
        request.put("receipt", receipt);
        if (notes != null && !notes.isEmpty()) {
            request.put("notes", new JSONObject(notes));
        }

        try {
            com.razorpay.Order order = client().orders.create(request);
            Object id = order.get("id");
            if (id == null) {
                throw new IllegalStateException("Razorpay did not return an order id");
            }
            return new CreatedOrder(String.valueOf(id), amountPaise, currency);
        } catch (RazorpayException ex) {
            throw new IllegalStateException("Razorpay order creation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean verifySignature(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        if (!isConfigured() || razorpayOrderId == null || razorpayPaymentId == null || razorpaySignature == null) {
            return false;
        }
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", razorpayOrderId);
            attributes.put("razorpay_payment_id", razorpayPaymentId);
            attributes.put("razorpay_signature", razorpaySignature);
            // The SDK recomputes HMAC-SHA256 and compares in constant time.
            return Utils.verifyPaymentSignature(attributes, keySecret);
        } catch (Exception ex) {
            log.warn("Razorpay signature verification rejected a callback: {}", ex.getMessage());
            return false;
        }
    }

    private RazorpayClient client() {
        RazorpayClient local = client;
        if (local == null) {
            synchronized (clientLock) {
                local = client;
                if (local == null) {
                    try {
                        local = new RazorpayClient(keyId, keySecret);
                    } catch (RazorpayException ex) {
                        throw new IllegalStateException("Could not initialise the Razorpay client", ex);
                    }
                    client = local;
                }
            }
        }
        return local;
    }
}
