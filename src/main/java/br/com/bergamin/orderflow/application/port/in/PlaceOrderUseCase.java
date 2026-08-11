package br.com.bergamin.orderflow.application.port.in;

import br.com.bergamin.orderflow.domain.model.Order;

import java.util.List;
import java.util.UUID;

/** Caso de uso: criar um pedido reservando estoque. */
public interface PlaceOrderUseCase {

    Result place(Command command);

    record Command(UUID customerId, List<Line> items, String idempotencyKey) {

        public record Line(UUID productId, int quantity) {
        }
    }

    /**
     * @param replayed {@code true} quando a chave de idempotencia ja existia e o pedido
     *                 retornado e o da requisicao original (o controller responde 200 em
     *                 vez de 201)
     */
    record Result(Order order, boolean replayed) {
    }
}
