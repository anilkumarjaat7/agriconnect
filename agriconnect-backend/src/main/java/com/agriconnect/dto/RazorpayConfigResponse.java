package com.agriconnect.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public checkout configuration. Contains only the publishable key id - the Razorpay secret
 * never leaves the server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RazorpayConfigResponse {
    /** False when AGRICONNECT's Razorpay credentials are absent; the UI then keeps the legacy flow. */
    private boolean enabled;
    private String keyId;
    private String currency;
}
