package com.fooddelivery.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItem entity — line items of an order.
 *
 * CONCEPT: Historical Price Snapshotting
 * ───────────────────────────────────────
 * `priceAtTime` and `nameAtTime` freeze the price and name of the item
 * at the exact moment the order was confirmed.
 * Even if the restaurant subsequently raises prices or renames the item,
 * the invoice and receipts remain immutable.
 */
@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "name_at_time", nullable = false, length = 150)
    private String nameAtTime;

    @Column(name = "price_at_time", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtTime;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "item_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemTotal;
}

