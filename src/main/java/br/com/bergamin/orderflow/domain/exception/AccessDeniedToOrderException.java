package br.com.bergamin.orderflow.domain.exception;

import java.util.UUID;

/**
 * Cliente tentando acessar pedido de outro cliente. Vira HTTP 403.
 *
 * <p>Separado de "nao encontrado" de proposito: a autorizacao por dono do recurso e uma
 * regra de negocio, nao um detalhe do Spring Security.</p>
 */
public class AccessDeniedToOrderException extends DomainException {

    public AccessDeniedToOrderException(UUID orderId) {
        super("pedido %s nao pertence ao usuario autenticado".formatted(orderId));
    }
}
