package com.agriconnect.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when Razorpay reports a payment but the signature does not verify.
 *
 * <p>It exists separately from {@link CustomException} so the payment transaction can be
 * annotated with {@code noRollbackFor}: the FAILED audit row and the order cancellation must be
 * committed even though the request still answers 400.
 */
public class PaymentVerificationFailedException extends CustomException {

    public PaymentVerificationFailedException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
