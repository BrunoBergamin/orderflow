package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/** Tabela {@code order_items}. */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderJpaEntity order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 60)
    private String sku;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private int quantity;

    /** Preco no momento da compra, nao o preco atual do produto. */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    protected OrderItemJpaEntity() {
        // exigido pelo JPA
    }

    public OrderItemJpaEntity(UUID id, UUID productId, String sku, String description,
                              int quantity, BigDecimal unitPrice) {
        this.id = id;
        this.productId = productId;
        this.sku = sku;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    void setOrder(OrderJpaEntity order) {
        this.order = order;
    }

    public OrderJpaEntity getOrder() {
        return order;
    }
}
