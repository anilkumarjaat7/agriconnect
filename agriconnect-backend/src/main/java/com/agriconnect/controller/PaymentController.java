package com.agriconnect.controller;

import com.agriconnect.dto.CancelPaymentRequest;
import com.agriconnect.dto.PaymentInitRequest;
import com.agriconnect.dto.PaymentInitResponse;
import com.agriconnect.dto.PaymentResponse;
import com.agriconnect.dto.RazorpayConfigResponse;
import com.agriconnect.dto.VerifyPaymentRequest;
import com.agriconnect.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Razorpay checkout endpoints. All routes are buyer-only (see SecurityConfig) and every call is
 * scoped to the JWT account by the service. The Razorpay secret is never returned here.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** Tells the UI whether Razorpay is configured and returns the publishable key id. */
    @GetMapping("/config")
    public ResponseEntity<RazorpayConfigResponse> getConfig() {
        return ResponseEntity.ok(paymentService.getConfig());
    }

    /** Creates an unpaid order plus a Razorpay order. Stock and cart are not touched yet. */
    @PostMapping("/create-order")
    public ResponseEntity<PaymentInitResponse> createOrder(
            @Valid @RequestBody(required = false) PaymentInitRequest request,
            Principal principal) {
        return ResponseEntity.ok(paymentService.createCheckout(request, principal.getName()));
    }

    /** Verifies the signature; only then is stock deducted and the cart cleared. */
    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verify(@Valid @RequestBody VerifyPaymentRequest request,
                                                  Principal principal) {
        return ResponseEntity.ok(paymentService.verifyPayment(request, principal.getName()));
    }

    /** Called when the buyer dismisses the checkout without paying. */
    @PostMapping("/cancel")
    public ResponseEntity<PaymentResponse> cancel(@Valid @RequestBody CancelPaymentRequest request,
                                                  Principal principal) {
        return ResponseEntity.ok(paymentService.cancelPayment(request, principal.getName()));
    }

    /** The signed-in buyer's own payment state for an order, if a checkout was ever started. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getByOrder(@PathVariable Long orderId, Principal principal) {
        return paymentService.getByOrder(orderId, principal.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
