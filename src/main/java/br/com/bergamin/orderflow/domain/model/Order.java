package br.com.bergamin.orderflow.domain.model;

import br.com.bergamin.orderflow.domain.event.DomainEvent;
import br.com.bergamin.orderflow.domain.event.OrderCancelled;
import br.com.bergamin.orderflow.domain.event.OrderPaid;
import br.com.bergamin.orderflow.domain.event.OrderPlaced;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz do agregado Pedido.
 *
 * <p>Concentra as regras que precisam valer sempre: pedido nasce com pelo menos um item,
 * o total e derivado dos itens (nunca vem do cliente) e so um pedido {@code PENDING} muda
 * de estado. Nao existe {@code setStatus} publico: quem chama descreve a intencao
 * ({@link #markPaid}, {@link #cancel}) e o agregado decide se e legal.</p>
 *
 * <p>Classe sem nenhuma anotacao de framework -- os testes de dominio rodam em
 * milissegundos, sem subir contexto Spring.</p>
 */
public class Order {

    private final UUID id;
    private final UUID customerId;
    private final List<OrderItem> items;
    private final Instant createdAt;

    private OrderStatus status;
    private Instant updatedAt;
    private String paymentTransactionId;
    private String statusReason;
    private final Long version;

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private Order(UUID id, UUID customerId, List<OrderItem> items, OrderStatus status,
                  Instant createdAt, Instant updatedAt, String paymentTransactionId,
                  String statusReason, Long version) {
        this.id = Objects.requireNonNull(id, "id e obrigatorio");
        this.customerId = Objects.requireNonNull(customerId, "customerId e obrigatorio");
        this.items = List.copyOf(Objects.requireNonNull(items, "items e obrigatorio"));
        this.status = Objects.requireNonNull(status, "status e obrigatorio");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt e obrigatorio");
        this.updatedAt = updatedAt;
        this.paymentTransactionId = paymentTransactionId;
        this.statusReason = statusReason;
        this.version = version;
    }

    /**
     * Cria um pedido novo e registra o evento {@code Order.Placed}.
     *
     * @throws IllegalArgumentException se a lista de itens estiver vazia ou tiver produto repetido
     */
    public static Order place(UUID customerId, List<OrderItem> items, Instant now) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("pedido precisa de pelo menos um item");
        }
        long distinctProducts = items.stream().map(OrderItem::productId).distinct().count();
        if (distinctProducts != items.size()) {
            throw new IllegalArgumentException("pedido nao pode ter o mesmo produto em linhas diferentes");
        }

        Order order = new Order(UUID.randomUUID(), customerId, items, OrderStatus.PENDING,
                now, now, null, null, null);

        List<OrderPlaced.Line> lines = items.stream()
                .map(item -> new OrderPlaced.Line(item.productId(), item.sku(), item.quantity()))
                .toList();
        order.registerEvent(new OrderPlaced(order.id, customerId, order.total(), lines, now));
        return order;
    }

    /** Reidrata um pedido vindo do banco. Nao gera eventos. */
    public static Order restore(UUID id, UUID customerId, List<OrderItem> items, OrderStatus status,
                                Instant createdAt, Instant updatedAt, String paymentTransactionId,
                                String statusReason, Long version) {
        return new Order(id, customerId, items, status, createdAt, updatedAt,
                paymentTransactionId, statusReason, version);
    }

    /** Total do pedido, sempre derivado dos itens. */
    public Money total() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.ZERO, Money::add);
    }

    /** Confirma o pagamento e registra {@code Order.Paid}. */
    public void markPaid(String transactionId, Instant now) {
        requireTransitionTo(OrderStatus.PAID);
        this.status = OrderStatus.PAID;
        this.paymentTransactionId = Objects.requireNonNull(transactionId, "transactionId e obrigatorio");
        this.updatedAt = now;
        registerEvent(new OrderPaid(id, transactionId, total(), now));
    }

    /** Marca a recusa do gateway e registra {@code Order.Cancelled} com {@code paymentDeclined=true}. */
    public void markPaymentFailed(String reason, Instant now) {
        requireTransitionTo(OrderStatus.PAYMENT_FAILED);
        this.status = OrderStatus.PAYMENT_FAILED;
        this.statusReason = reason;
        this.updatedAt = now;
        registerEvent(new OrderCancelled(id, reason, true, now));
    }

    /** Cancelamento a pedido do cliente e registra {@code Order.Cancelled}. */
    public void cancel(String reason, Instant now) {
        requireTransitionTo(OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
        this.statusReason = reason;
        this.updatedAt = now;
        registerEvent(new OrderCancelled(id, reason, false, now));
    }

    /** Indica se o estoque dos itens deve voltar para a prateleira. */
    public boolean requiresStockRelease() {
        return status == OrderStatus.CANCELLED || status == OrderStatus.PAYMENT_FAILED;
    }

    public boolean belongsTo(UUID otherCustomerId) {
        return customerId.equals(otherCustomerId);
    }

    private void requireTransitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStateException(id, status, target);
        }
    }

    private void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /**
     * Devolve os eventos acumulados e limpa a lista.
     *
     * <p>Chamado uma unica vez pelo caso de uso, ja dentro da transacao, logo antes de
     * gravar na outbox. Limpar evita republicar o mesmo evento se o agregado for
     * reaproveitado.</p>
     */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> pulled = List.copyOf(domainEvents);
        domainEvents.clear();
        return pulled;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPaymentTransactionId() {
        return paymentTransactionId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Long getVersion() {
        return version;
    }
}
