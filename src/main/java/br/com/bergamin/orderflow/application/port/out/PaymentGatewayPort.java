package br.com.bergamin.orderflow.application.port.out;

import br.com.bergamin.orderflow.domain.model.Money;

import java.util.UUID;

/**
 * Porta de saida para o adquirente.
 *
 * <p>Recusa de pagamento nao e excecao: e um resultado esperado do negocio e vem no
 * retorno. Excecao fica reservada para falha de comunicacao com o gateway.</p>
 */
public interface PaymentGatewayPort {

    PaymentResult charge(PaymentRequest request);

    record PaymentRequest(UUID orderId, Money amount, String paymentToken) {
    }

    record PaymentResult(boolean approved, String transactionId, String declineReason) {

        public static PaymentResult approved(String transactionId) {
            return new PaymentResult(true, transactionId, null);
        }

        public static PaymentResult declined(String reason) {
            return new PaymentResult(false, null, reason);
        }
    }
}
