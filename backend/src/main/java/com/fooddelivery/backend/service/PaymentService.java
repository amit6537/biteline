package com.fooddelivery.backend.service;

import com.fooddelivery.backend.dto.request.ProcessPaymentRequest;
import com.fooddelivery.backend.dto.response.PaymentResponse;
import com.fooddelivery.backend.entity.*;
import com.fooddelivery.backend.exception.BadRequestException;
import com.fooddelivery.backend.exception.ConflictException;
import com.fooddelivery.backend.exception.ForbiddenException;
import com.fooddelivery.backend.exception.ResourceNotFoundException;
import com.fooddelivery.backend.kafka.OrderEventProducer;
import com.fooddelivery.backend.kafka.OrderEvents;
import com.fooddelivery.backend.repository.OrderRepository;
import com.fooddelivery.backend.repository.PaymentRepository;
import com.fooddelivery.backend.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * PaymentService — Handles payment initiation, idempotency checks, and mock gateway resolution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;
    private final OrderEventProducer eventProducer;

    @Transactional
    public PaymentResponse processPayment(ProcessPaymentRequest request) {
        User user = securityUtils.getCurrentUser();

        String idemKey = (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank())
                ? null : request.getIdempotencyKey();

        // 1. Lock the order row. Two payment requests for the same order
        //    (double-click, network retry) now run one at a time, so the
        //    second one always sees the first one's payment.
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (!order.getCustomer().getId().equals(user.getId())) {
            throw new ForbiddenException("You cannot pay for an order that does not belong to you.");
        }

        // 2. Idempotency check (after the lock, so concurrent retries are safe).
        //    A key is only a valid "replay" for the SAME order — otherwise a
        //    user could send someone else's key and read their payment.
        if (idemKey != null) {
            Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(idemKey);
            if (existingOpt.isPresent()) {
                Payment existing = existingOpt.get();
                if (!existing.getOrder().getId().equals(order.getId())) {
                    throw new ConflictException("Idempotency key was already used for a different request.");
                }
                log.info("Idempotent replay detected for key: {}", idemKey);
                return toPaymentResponse(existing);
            }
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("Cannot make payment for a cancelled order.");
        }

        // Check if order already has an active or completed payment
        Optional<Payment> existingPaymentOpt = paymentRepository.findByOrderId(order.getId());
        if (existingPaymentOpt.isPresent() && existingPaymentOpt.get().getStatus() == PaymentStatus.SUCCESS) {
            throw new BadRequestException("Order is already paid.");
        }

        // 3. Create or reuse payment record
        Payment payment = existingPaymentOpt.orElseGet(() -> Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .build());

        payment.setMethod(request.getMethod());
        payment.setIdempotencyKey(idemKey);

        // 4. Simulate payment processing
        // (In real life: Call Stripe/Razorpay SDK or generate payment gateway URL)
        String mockTxnId = "TXN_" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        payment.setTransactionId(mockTxnId);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(payment);
        log.info("Payment processed successfully: paymentId={}, orderId={}, amount={}, txnId={}",
                saved.getId(), order.getId(), saved.getAmount(), mockTxnId);

        // Publish PaymentSuccessEvent → NotificationConsumer will confirm to customer
        eventProducer.publishPaymentSuccess(new OrderEvents.PaymentSuccessEvent(
                saved.getId(),
                order.getId(),
                order.getCustomer().getId(),
                order.getCustomer().getEmail(),
                saved.getAmount(),
                mockTxnId,
                saved.getPaidAt()
        ));

        return toPaymentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        User user = securityUtils.getCurrentUser();
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for order: " + orderId));

        boolean isCustomer = payment.getOrder().getCustomer().getId().equals(user.getId());
        boolean isOwner = payment.getOrder().getRestaurant().getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == Role.ADMIN;

        if (!isCustomer && !isOwner && !isAdmin) {
            throw new ForbiddenException("Unauthorized to view payment details.");
        }

        return toPaymentResponse(payment);
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .method(payment.getMethod())
                .transactionId(payment.getTransactionId())
                .idempotencyKey(payment.getIdempotencyKey())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}

