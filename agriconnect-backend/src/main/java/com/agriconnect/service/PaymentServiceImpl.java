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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final RazorpayGateway razorpayGateway;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              OrderRepository orderRepository,
                              UserRepository userRepository,
                              OrderService orderService,
                              RazorpayGateway razorpayGateway) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.orderService = orderService;
        this.razorpayGateway = razorpayGateway;
    }

    @Override
    public RazorpayConfigResponse getConfig() {
        boolean enabled = razorpayGateway.isConfigured();
        return RazorpayConfigResponse.builder()
                .enabled(enabled)
                .keyId(enabled ? razorpayGateway.getKeyId() : null)
                .currency(razorpayGateway.getCurrency())
                .build();
    }

    /**
     * Not transactional on purpose: the Razorpay HTTP call must not hold a database connection.
     * The order is committed first by {@code createPendingPaymentOrder}, then the gateway is
     * called, then the payment row is written. A gateway failure retires the unpaid order.
     */
    @Override
    public PaymentInitResponse createCheckout(PaymentInitRequest request, String buyerEmail) {
        if (!razorpayGateway.isConfigured()) {
            throw new CustomException("Razorpay is not configured on the server.", HttpStatus.SERVICE_UNAVAILABLE);
        }

        User buyer = resolveBuyer(buyerEmail);
        String note = request == null ? null : request.getNote();

        // Builds the order snapshot but does NOT touch stock or the cart.
        OrderResponse order = orderService.createPendingPaymentOrder(note, buyerEmail);

        long amountPaise;
        try {
            amountPaise = toPaise(order.getTotalAmount());
        } catch (ArithmeticException ex) {
            cancelQuietly(order.getId(), buyerEmail);
            throw new CustomException("Order total is not a valid payment amount.", HttpStatus.BAD_REQUEST);
        }
        if (amountPaise < 100) {
            cancelQuietly(order.getId(), buyerEmail);
            throw new CustomException("Razorpay requires an order total of at least Rs.1.", HttpStatus.BAD_REQUEST);
        }

        RazorpayGateway.CreatedOrder created;
        try {
            created = razorpayGateway.createOrder(order.getTotalAmount(), "AGR-" + order.getId(),
                    Map.of("orderId", String.valueOf(order.getId())));
        } catch (RuntimeException ex) {
            log.warn("Razorpay order creation failed for order {}: {}", order.getId(), ex.getMessage());
            cancelQuietly(order.getId(), buyerEmail);
            throw new CustomException("Could not start Razorpay checkout. Please try again.", HttpStatus.BAD_GATEWAY);
        }

        Order orderEntity = orderRepository.findById(order.getId())
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        paymentRepository.save(Payment.builder()
                .order(orderEntity)
                .buyer(buyer)
                .razorpayOrderId(created.id())
                .amount(order.getTotalAmount())
                .currency(created.currency())
                .status(Payment.STATUS_CREATED)
                .build());

        return PaymentInitResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .razorpayOrderId(created.id())
                .amount(order.getTotalAmount())
                .amountPaise(created.amountPaise())
                .currency(created.currency())
                .keyId(razorpayGateway.getKeyId())
                .buyerName(fullName(buyer))
                .buyerEmail(buyer.getEmail())
                .buyerContact(buyer.getPhoneNumber())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = PaymentVerificationFailedException.class)
    public PaymentResponse verifyPayment(VerifyPaymentRequest request, String buyerEmail) {
        User buyer = resolveBuyer(buyerEmail);
        Order order = orderRepository.findByIdAndBuyerId(request.getOrderId(), buyer.getId())
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        // Row lock: two callbacks for the same checkout are serialised here.
        Payment payment = paymentRepository.findWithLockByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new CustomException("Payment not found for this checkout.", HttpStatus.NOT_FOUND));

        if (payment.getOrder() == null || !order.getId().equals(payment.getOrder().getId())) {
            throw new CustomException("This payment does not belong to the supplied order.", HttpStatus.BAD_REQUEST);
        }
        if (payment.getBuyer() == null || !buyer.getId().equals(payment.getBuyer().getId())) {
            throw new CustomException("This payment does not belong to your account.", HttpStatus.FORBIDDEN);
        }

        // Idempotent: Razorpay can report the same success more than once. The second callback
        // must never deduct stock again, so it simply replays the stored result.
        if (Payment.STATUS_SUCCESS.equals(payment.getStatus())) {
            return toResponse(payment);
        }
        if (!Payment.STATUS_CREATED.equals(payment.getStatus())) {
            throw new CustomException("This payment is already " + payment.getStatus().toLowerCase()
                    + " and can no longer be verified.", HttpStatus.CONFLICT);
        }

        Optional<Payment> reused = paymentRepository.findByRazorpayPaymentId(request.getRazorpayPaymentId());
        if (reused.isPresent() && !reused.get().getId().equals(payment.getId())) {
            throw new CustomException("This Razorpay payment has already been processed.", HttpStatus.CONFLICT);
        }

        boolean signatureValid = razorpayGateway.verifySignature(
                request.getRazorpayOrderId(), request.getRazorpayPaymentId(), request.getRazorpaySignature());
        if (!signatureValid) {
            // noRollbackFor on this method keeps this audit trail: nothing was fulfilled.
            payment.setStatus(Payment.STATUS_FAILED);
            payment.setFailureReason("Razorpay signature verification failed");
            paymentRepository.save(payment);
            if (Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
                order.setStatus(Order.STATUS_CANCELLED);
                orderRepository.save(order);
            }
            throw new PaymentVerificationFailedException(
                    "Payment verification failed. No stock was reserved and your cart is unchanged.");
        }

        // Signature proves the callback came from Razorpay for the order we created. Only now is
        // stock deducted and the cart cleared, exactly once, inside this transaction.
        orderService.finalizePaidOrder(order.getId(), buyerEmail);

        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(Payment.STATUS_SUCCESS);
        payment.setFailureReason(null);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        return toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(CancelPaymentRequest request, String buyerEmail) {
        User buyer = resolveBuyer(buyerEmail);
        Order order = orderRepository.findByIdAndBuyerId(request.getOrderId(), buyer.getId())
                .orElseThrow(() -> new CustomException("Order not found", HttpStatus.NOT_FOUND));

        Payment payment = paymentRepository.findWithLockByOrderId(order.getId())
                .orElseThrow(() -> new CustomException("No payment was started for this order.", HttpStatus.NOT_FOUND));

        if (Payment.STATUS_SUCCESS.equals(payment.getStatus())) {
            throw new CustomException("This payment has already been completed and cannot be cancelled.",
                    HttpStatus.CONFLICT);
        }
        if (Payment.STATUS_CANCELLED.equals(payment.getStatus())) {
            return toResponse(payment);
        }

        payment.setStatus(Payment.STATUS_CANCELLED);
        if (request.getReason() != null && !request.getReason().isBlank()) {
            payment.setFailureReason(truncate(request.getReason().trim(), 255));
        }
        paymentRepository.save(payment);

        if (Order.STATUS_PENDING_PAYMENT.equals(order.getStatus())) {
            order.setStatus(Order.STATUS_CANCELLED);
            orderRepository.save(order);
        }
        return toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> getByOrder(Long orderId, String buyerEmail) {
        User buyer = resolveBuyer(buyerEmail);
        return paymentRepository.findByOrderId(orderId)
                .filter(payment -> payment.getBuyer() != null && buyer.getId().equals(payment.getBuyer().getId()))
                .map(this::toResponse);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder() == null ? null : payment.getOrder().getId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private User resolveBuyer(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("Buyer account not found", HttpStatus.UNAUTHORIZED));
    }

    private static long toPaise(BigDecimal amount) {
        if (amount == null) {
            throw new ArithmeticException("amount is null");
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static String fullName(User user) {
        return (user.getFirstName() + " " + user.getLastName()).trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void cancelQuietly(Long orderId, String buyerEmail) {
        try {
            orderService.cancelPendingOrder(orderId, buyerEmail);
        } catch (RuntimeException ex) {
            log.warn("Could not retire pending order {}: {}", orderId, ex.getMessage());
        }
    }
}
