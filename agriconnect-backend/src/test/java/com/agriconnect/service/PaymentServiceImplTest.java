package com.agriconnect.service;

import com.agriconnect.dto.CancelPaymentRequest;
import com.agriconnect.dto.OrderResponse;
import com.agriconnect.dto.PaymentInitRequest;
import com.agriconnect.dto.PaymentInitResponse;
import com.agriconnect.dto.PaymentResponse;
import com.agriconnect.dto.RazorpayConfigResponse;
import com.agriconnect.dto.VerifyPaymentRequest;
import com.agriconnect.entity.Order;
import com.agriconnect.entity.Payment;
import com.agriconnect.entity.User;
import com.agriconnect.exception.CustomException;
import com.agriconnect.exception.PaymentVerificationFailedException;
import com.agriconnect.repository.OrderRepository;
import com.agriconnect.repository.PaymentRepository;
import com.agriconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final String BUYER_EMAIL = "buyer@example.com";

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderService orderService;
    @Mock private RazorpayGateway razorpayGateway;

    @InjectMocks private PaymentServiceImpl paymentService;

    private User buyer;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {
        buyer = User.builder()
                .id(7L)
                .email(BUYER_EMAIL)
                .firstName("Bina")
                .lastName("Rao")
                .phoneNumber("9999999999")
                .build();
        order = Order.builder()
                .id(42L)
                .buyer(buyer)
                .status(Order.STATUS_PENDING_PAYMENT)
                .orderNumber("ORD-000042")
                .totalAmount(new BigDecimal("250.00"))
                .build();
        payment = Payment.builder()
                .id(5L)
                .order(order)
                .buyer(buyer)
                .razorpayOrderId("order_test_1")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .status(Payment.STATUS_CREATED)
                .build();
    }

    @Test
    void configIsDisabledWhenCredentialsAreMissing() {
        when(razorpayGateway.isConfigured()).thenReturn(false);
        when(razorpayGateway.getCurrency()).thenReturn("INR");

        RazorpayConfigResponse response = paymentService.getConfig();

        assertFalse(response.isEnabled());
        assertNull(response.getKeyId());
        assertEquals("INR", response.getCurrency());
    }

    @Test
    void configExposesOnlyThePublishableKey() {
        when(razorpayGateway.isConfigured()).thenReturn(true);
        when(razorpayGateway.getKeyId()).thenReturn("rzp_test_key");
        when(razorpayGateway.getCurrency()).thenReturn("INR");

        RazorpayConfigResponse response = paymentService.getConfig();

        assertTrue(response.isEnabled());
        assertEquals("rzp_test_key", response.getKeyId());
    }

    @Test
    void checkoutRefusesToStartWithoutCredentials() {
        when(razorpayGateway.isConfigured()).thenReturn(false);

        CustomException ex = assertThrows(CustomException.class,
                () -> paymentService.createCheckout(new PaymentInitRequest(), BUYER_EMAIL));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        verifyNoInteractions(orderService);
    }

    @Test
    void checkoutCreatesAnUnpaidOrderBeforeReturningTheKey() {
        when(razorpayGateway.isConfigured()).thenReturn(true);
        when(razorpayGateway.getKeyId()).thenReturn("rzp_test_key");
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(orderService.createPendingPaymentOrder(null, BUYER_EMAIL)).thenReturn(OrderResponse.builder()
                .id(42L)
                .orderNumber("ORD-000042")
                .status(Order.STATUS_PENDING_PAYMENT)
                .totalAmount(new BigDecimal("250.00"))
                .build());
        when(razorpayGateway.createOrder(eq(new BigDecimal("250.00")), eq("AGR-42"), anyMap()))
                .thenReturn(new RazorpayGateway.CreatedOrder("order_test_1", 25000L, "INR"));
        when(orderRepository.findById(42L)).thenReturn(Optional.of(order));

        PaymentInitResponse response = paymentService.createCheckout(new PaymentInitRequest(), BUYER_EMAIL);

        assertEquals(42L, response.getOrderId());
        assertEquals("order_test_1", response.getRazorpayOrderId());
        assertEquals(25000L, response.getAmountPaise());
        assertEquals("rzp_test_key", response.getKeyId());
        assertEquals("Bina Rao", response.getBuyerName());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertEquals(Payment.STATUS_CREATED, saved.getStatus());
        assertEquals("order_test_1", saved.getRazorpayOrderId());
        assertEquals(new BigDecimal("250.00"), saved.getAmount());
        // Nothing is deducted until the signature is verified.
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyString());
    }

    @Test
    void aVerifiedPaymentFulfilsTheOrderExactlyOnce() {
        stubSuccessfulVerification();

        PaymentResponse first = paymentService.verifyPayment(validRequest("sig"), BUYER_EMAIL);
        assertEquals(Payment.STATUS_SUCCESS, first.getStatus());
        assertEquals("pay_1", first.getRazorpayPaymentId());
        assertNotNull(first.getPaidAt());

        // Razorpay is allowed to deliver the same success twice; the second call must not deduct
        // stock or clear the cart again.
        PaymentResponse second = paymentService.verifyPayment(validRequest("sig"), BUYER_EMAIL);
        assertEquals(Payment.STATUS_SUCCESS, second.getStatus());

        verify(orderService, times(1)).finalizePaidOrder(42L, BUYER_EMAIL);
        verify(razorpayGateway, times(1)).verifySignature(anyString(), anyString(), anyString());
    }

    @Test
    void aForgedSignatureNeverConsumesStock() {
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByRazorpayOrderId("order_test_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(razorpayGateway.verifySignature("order_test_1", "pay_1", "forged")).thenReturn(false);

        assertThrows(PaymentVerificationFailedException.class,
                () -> paymentService.verifyPayment(validRequest("forged"), BUYER_EMAIL));

        assertEquals(Payment.STATUS_FAILED, payment.getStatus());
        assertEquals(Order.STATUS_CANCELLED, order.getStatus());
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyString());
        verify(paymentRepository).save(payment);
        verify(orderRepository).save(order);
    }

    @Test
    void closingTheCheckoutCancelsTheAttemptWithoutTouchingStock() {
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(42L)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.cancelPayment(
                CancelPaymentRequest.builder().orderId(42L).reason("closed by buyer").build(), BUYER_EMAIL);

        assertEquals(Payment.STATUS_CANCELLED, response.getStatus());
        assertEquals("closed by buyer", response.getFailureReason());
        assertEquals(Order.STATUS_CANCELLED, order.getStatus());
        verify(orderService, never()).finalizePaidOrder(anyLong(), anyString());
    }

    @Test
    void aPaidPaymentCannotBeCancelled() {
        payment.setStatus(Payment.STATUS_SUCCESS);
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByOrderId(42L)).thenReturn(Optional.of(payment));

        CustomException ex = assertThrows(CustomException.class, () -> paymentService.cancelPayment(
                CancelPaymentRequest.builder().orderId(42L).build(), BUYER_EMAIL));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    private void stubSuccessfulVerification() {
        when(userRepository.findByEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        when(paymentRepository.findWithLockByRazorpayOrderId("order_test_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.findByRazorpayPaymentId("pay_1")).thenReturn(Optional.empty());
        when(razorpayGateway.verifySignature("order_test_1", "pay_1", "sig")).thenReturn(true);
    }

    private VerifyPaymentRequest validRequest(String signature) {
        return VerifyPaymentRequest.builder()
                .orderId(42L)
                .razorpayOrderId("order_test_1")
                .razorpayPaymentId("pay_1")
                .razorpaySignature(signature)
                .build();
    }
}
