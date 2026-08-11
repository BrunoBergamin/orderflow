package br.com.bergamin.orderflow.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida do controle de idempotencia.
 *
 * <p>O cliente manda o cabecalho {@code Idempotency-Key} ao criar um pedido. Se a rede cair
 * depois do POST e o app do cliente repetir a chamada, a chave ja registrada devolve o
 * mesmo pedido em vez de criar um duplicado. O problema classico de "cliente clicou duas
 * vezes em comprar".</p>
 */
public interface IdempotencyPort {

    /** Retorna o pedido ja criado para esta chave, se existir. */
    Optional<UUID> findOrderIdByKey(String idempotencyKey, UUID customerId);

    /**
     * Amarra a chave ao pedido criado.
     *
     * @return {@code false} se a chave ja tinha sido registrada por uma requisicao
     *         concorrente (a unicidade e garantida pelo banco, nao pelo check anterior)
     */
    boolean register(String idempotencyKey, UUID customerId, UUID orderId);
}
