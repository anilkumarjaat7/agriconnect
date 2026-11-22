package com.agriconnect.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Sent when the buyer closes the Razorpay modal without completing a payment. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelPaymentRequest {

    @NotNull(message = "Order id is required")
    private Long orderId;

    @Size(max = 255, message = "Reason must be less than 255 characters")
    private String reason;
}
