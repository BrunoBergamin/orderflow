package br.com.bergamin.orderflow.application.service;

import br.com.bergamin.orderflow.application.port.in.PayOrderUseCase;
import br.com.bergamin.orderflow.application.port.out.OrderRepositoryPort;
import br.com.bergamin.orderflow.application.port.out.PaymentGatewayPort;
import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cobra o pedido no gateway.
 *
 * <p><b>Decisao de projeto:</b> este metodo <i>nao</i> e transacional. A chamada ao
 * adquirente e I/O de rede, e segurar uma conexao do pool do banco aberta durante ela e a
 * receita para esgotar o pool sob carga. O fluxo e: le o pedido, cobra fora de transacao e
 * so entao abre uma transacao curta ({@link OrderPaymentApplier}) para gravar o resultado.</p>
 *
 * <p>A janela entre ler e gravar e protegida por lock otimista: se outra requisicao mexer no
 * pedido nesse meio-tempo, a gravacao falha e o cliente recebe 409 em vez de uma cobranca
 * aplicada em cima de um estado velho.</p>
 */
@Service
public class PayOrderService implements PayOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(PayOrderService.class);

    private final OrderRepositoryPort orders;
    private final PaymentGatewayPort paymentGateway;
    private final OrderPaymentApplier applier;

    public PayOrderService(OrderRepositoryPort orders,
                           PaymentGatewayPort paymentGateway,
                           OrderPaymentApplier applier) {
        this.orders = orders;
        this.paymentGateway = paymentGateway;
        this.applier = applier;
    }

    @Override
    public Order pay(Command command) {
        Order order = orders.findById(command.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido", command.orderId()));

        if (!order.belongsTo(command.customerId())) {
            throw new AccessDeniedToOrderException(order.getId());
        }
        // Falha antes de cobrar: nao ha por que chamar o adquirente de um pedido ja finalizado.
        if (order.getStatus().isFinal()) {
            throw new InvalidOrderStateException(order.getId(), order.getStatus(), OrderStatus.PAID);
        }

        PaymentGatewayPort.PaymentResult result = paymentGateway.charge(
                new PaymentGatewayPort.PaymentRequest(order.getId(), order.total(), command.paymentToken()));

        log.info("gateway respondeu {} para o pedido {}",
                result.approved() ? "aprovado" : "recusado (" + result.declineReason() + ")", order.getId());

        return applier.apply(order.getId(), result);
    }
}
