package com.agriconnect.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Optional note the buyer can attach to the order (same field the legacy checkout used). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitRequest {

    @Size(max = 500, message = "Note must be less than 500 characters")
    private String note;
}
