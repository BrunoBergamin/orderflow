package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import br.com.bergamin.orderflow.domain.model.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tabela {@code orders}.
 *
 * <p>Modelo de persistencia separado do modelo de dominio de proposito: assim o
 * {@code Order} do dominio nao carrega anotacoes JPA nem construtor vazio publico, e uma
 * mudanca de schema (uma coluna denormalizada, um indice) nao vira mudanca de regra de
 * negocio. O preco e um mapeador explicito.</p>
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    /** Total denormalizado: evita somar itens em toda listagem e em relatorios. */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payment_transaction_id", length = 100)
    private String paymentTransactionId;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /**
     * LAZY de proposito: quem lista pedidos nem sempre precisa dos itens. Onde precisa, o
     * repositorio usa {@code @EntityGraph} (consulta unica) e, nas listagens paginadas,
     * {@code hibernate.default_batch_fetch_size} carrega os itens de todas as linhas da
     * pagina em um unico {@code IN (...)}.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
        // exigido pelo JPA
    }

    public OrderJpaEntity(UUID id, UUID customerId, OrderStatus status, BigDecimal totalAmount,
                          String paymentTransactionId, String statusReason,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.paymentTransactionId = paymentTransactionId;
        this.statusReason = statusReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void addItem(OrderItemJpaEntity item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public void setPaymentTransactionId(String paymentTransactionId) {
        this.paymentTransactionId = paymentTransactionId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public List<OrderItemJpaEntity> getItems() {
        return items;
    }
}
