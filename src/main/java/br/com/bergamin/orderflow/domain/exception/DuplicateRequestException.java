package br.com.bergamin.orderflow.domain.exception;

/**
 * Duas requisicoes com a mesma {@code Idempotency-Key} chegaram ao mesmo tempo. Vira HTTP 409.
 *
 * <p>Quem perde a corrida recebe 409 e, ao repetir, cai no caminho de replay e recebe o
 * pedido original. E o comportamento correto: nunca dois pedidos para a mesma chave.</p>
 */
public class DuplicateRequestException extends DomainException {

    public DuplicateRequestException(String idempotencyKey) {
        super("requisicao concorrente com a mesma Idempotency-Key: " + idempotencyKey);
    }
}
