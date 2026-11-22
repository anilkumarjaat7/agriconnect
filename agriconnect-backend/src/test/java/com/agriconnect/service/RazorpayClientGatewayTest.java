package com.agriconnect.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the real HMAC-SHA256 check used by the SDK, without any network access. The
 * signature Razorpay sends is {@code hex(HMAC_SHA256(order_id + "|" + payment_id, key_secret))}.
 */
class RazorpayClientGatewayTest {

    private static final String SECRET = "test_secret_key";

    @Test
    void acceptsASignatureProducedWithTheKeySecret() throws Exception {
        RazorpayClientGateway gateway = new RazorpayClientGateway("rzp_test_key", SECRET, "INR");
        String signature = hmacSha256Hex("order_abc|pay_xyz", SECRET);

        assertTrue(gateway.verifySignature("order_abc", "pay_xyz", signature));
    }

    @Test
    void rejectsAForgedSignature() {
        RazorpayClientGateway gateway = new RazorpayClientGateway("rzp_test_key", SECRET, "INR");

        assertFalse(gateway.verifySignature("order_abc", "pay_xyz", "deadbeef"));
        assertFalse(gateway.verifySignature("order_abc", "pay_xyz", null));
    }

    @Test
    void rejectsEverythingWhenCredentialsAreMissing() {
        RazorpayClientGateway gateway = new RazorpayClientGateway("", "", "INR");

        assertFalse(gateway.isConfigured());
        assertFalse(gateway.verifySignature("order_abc", "pay_xyz", "anything"));
    }

    @Test
    void exposesOnlyThePublishableKey() {
        RazorpayClientGateway gateway = new RazorpayClientGateway("rzp_test_key", SECRET, "INR");

        assertTrue(gateway.isConfigured());
        assertEquals("rzp_test_key", gateway.getKeyId());
        assertEquals("INR", gateway.getCurrency());
    }

    private static String hmacSha256Hex(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
