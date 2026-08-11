package br.com.bergamin.orderflow.domain.exception;

import br.com.bergamin.orderflow.domain.model.OrderStatus;

import java.util.UUID;

/** Transicao de status nao permitida (ex.: pagar um pedido ja cancelado). Vira HTTP 409. */
public class InvalidOrderStateException extends DomainException {

    private final UUID orderId;
    private final OrderStatus current;
    private final OrderStatus target;

    public InvalidOrderStateException(UUID orderId, OrderStatus current, OrderStatus target) {
        super("pedido %s esta %s e nao pode ir para %s".formatted(orderId, current, target));
        this.orderId = orderId;
        this.current = current;
        this.target = target;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrent() {
        return current;
    }

    public OrderStatus getTarget() {
        return target;
    }
}
