package br.com.bergamin.orderflow.application.port.in;

import br.com.bergamin.orderflow.domain.model.Order;

import java.util.UUID;

/** Caso de uso: cobrar o pedido no gateway e aplicar o resultado. */
public interface PayOrderUseCase {

    Order pay(Command command);

    record Command(UUID orderId, UUID customerId, String paymentToken) {
    }
}
