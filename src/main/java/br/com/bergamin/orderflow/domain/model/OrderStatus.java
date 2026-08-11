package br.com.bergamin.orderflow.domain.model;

import java.util.Set;

/**
 * Ciclo de vida do pedido.
 *
 * <p>As transicoes validas ficam declaradas aqui, no dominio, e nao espalhadas em ifs
 * pelos servicos. Qualquer caminho novo (ex.: estorno) e uma alteracao local.</p>
 */
public enum OrderStatus {

    /** Criado, aguardando pagamento. */
    PENDING,
    /** Pagamento aprovado pelo gateway. */
    PAID,
    /** Gateway recusou o pagamento; estoque devolvido. */
    PAYMENT_FAILED,
    /** Cancelado pelo cliente antes do pagamento; estoque devolvido. */
    CANCELLED;

    private static final Set<OrderStatus> FROM_PENDING = Set.of(PAID, PAYMENT_FAILED, CANCELLED);

    public boolean canTransitionTo(OrderStatus target) {
        return this == PENDING && FROM_PENDING.contains(target);
    }

    public boolean isFinal() {
        return this != PENDING;
    }
}
