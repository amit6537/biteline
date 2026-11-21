package com.fooddelivery.backend.entity;

/** What a notification is about — lets the app pick an icon / screen to open. */
public enum NotificationType {
    ORDER_PLACED,        // customer: we got your order
    NEW_ORDER,           // owner:    a new order came in
    PAYMENT_SUCCESS,     // customer: payment received
    ORDER_STATUS,        // customer: accepted / preparing / ready / on the way / delivered
    ORDER_CANCELLED,     // customer + owner: order cancelled (with refund info)
    PARTNER_ASSIGNED,    // customer: partner X will deliver
    NEW_DELIVERY_JOB,    // partner:  you have a new pickup
    REVIEW_REQUEST,      // customer: rate your order (Phase 11)
    DELIVERY_DELAYED,    // customer: still looking for a delivery partner
    OPS_ALERT,           // admin:    an order is stuck, needs a human
    NEW_REVIEW           // owner/partner: you received a review (Phase 11)
}
