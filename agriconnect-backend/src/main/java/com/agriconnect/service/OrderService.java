package com.agriconnect.service;

import com.agriconnect.dto.OrderResponse;
import com.agriconnect.dto.ReceivedOrderResponse;

import java.util.List;

public interface OrderService {

    /**
     * Turns the buyer's current cart into a placed order. The whole order is rejected if any
     * line has become unavailable, deleted, or exceeds the remaining stock.
     */
    OrderResponse placeOrder(String note, String buyerEmail);

    /**
     * Creates an order from the current cart without consuming stock or clearing the cart.
     * Used to obtain the amount before the buyer is sent to Razorpay Checkout.
     */
    OrderResponse createPendingPaymentOrder(String note, String buyerEmail);

    /**
     * Fulfils an order whose payment has already been verified: deducts stock exactly once,
     * clears the paid cart lines and moves the order to PLACED. Idempotent.
     */
    OrderResponse finalizePaidOrder(Long orderId, String buyerEmail);

    /** Cancels an unpaid order. Leaves stock and the cart untouched. */
    void cancelPendingOrder(Long orderId, String buyerEmail);

    List<OrderResponse> getMyOrders(String buyerEmail);

    /** Ownership-scoped: a buyer can only ever read their own order. */
    OrderResponse getOrderById(Long orderId, String buyerEmail);

    /** Orders containing at least one of this farmer's crops, trimmed to their own lines. */
    List<ReceivedOrderResponse> getReceivedOrders(String farmerEmail);

    /** Orders for crops belonging to CollectedFarmers collected by this coordinator, trimmed to their lines. */
    List<ReceivedOrderResponse> getCoordinatorReceivedOrders(String coordinatorEmail);

    /** All orders for admin monitoring. */
    List<OrderResponse> getAllOrders();

    /** Update order status (seller/admin). */
    OrderResponse updateOrderStatus(Long orderId, String newStatus, String userEmail);
}
