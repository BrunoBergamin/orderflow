package br.com.bergamin.orderflow.infrastructure.adapter.in.rest;

import br.com.bergamin.orderflow.application.common.PageQuery;
import br.com.bergamin.orderflow.application.port.in.CancelOrderUseCase;
import br.com.bergamin.orderflow.application.port.in.FindOrderUseCase;
import br.com.bergamin.orderflow.application.port.in.PayOrderUseCase;
import br.com.bergamin.orderflow.application.port.in.PlaceOrderUseCase;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto.OrderDtos;
import br.com.bergamin.orderflow.infrastructure.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Recurso de pedidos.
 *
 * <p>O controller nao tem regra: converte HTTP em comando, chama o caso de uso e converte a
 * resposta. Nem o {@code customerId} vem do corpo -- ele sai do token, senao qualquer
 * cliente criaria pedido no nome de outro.</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Pedidos", description = "Criacao, pagamento, cancelamento e consulta de pedidos")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final PlaceOrderUseCase placeOrder;
    private final PayOrderUseCase payOrder;
    private final CancelOrderUseCase cancelOrder;
    private final FindOrderUseCase findOrder;

    public OrderController(PlaceOrderUseCase placeOrder,
                           PayOrderUseCase payOrder,
                           CancelOrderUseCase cancelOrder,
                           FindOrderUseCase findOrder) {
        this.placeOrder = placeOrder;
        this.payOrder = payOrder;
        this.cancelOrder = cancelOrder;
        this.findOrder = findOrder;
    }

    @PostMapping
    @Operation(summary = "Cria um pedido e reserva o estoque",
            description = """
                    Envie o cabecalho `Idempotency-Key` com um valor unico por tentativa de compra.
                    Repetir a chamada com a mesma chave devolve o pedido original com status 200,
                    em vez de criar um pedido duplicado.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "200", description = "Replay de uma Idempotency-Key ja usada"),
            @ApiResponse(responseCode = "422", description = "Estoque insuficiente"),
            @ApiResponse(responseCode = "404", description = "Produto inexistente")
    })
    public ResponseEntity<OrderDtos.OrderResponse> place(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "Chave de idempotencia da tentativa de compra")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody OrderDtos.PlaceOrderRequest request) {

        var command = new PlaceOrderUseCase.Command(
                user.id(),
                request.items().stream()
                        .map(item -> new PlaceOrderUseCase.Command.Line(item.productId(), item.quantity()))
                        .toList(),
                idempotencyKey);

        PlaceOrderUseCase.Result result = placeOrder.place(command);
        OrderDtos.OrderResponse body = OrderDtos.OrderResponse.from(result.order());

        if (result.replayed()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + result.order().getId()))
                .body(body);
    }

    @PostMapping("/{orderId}/payment")
    @Operation(summary = "Cobra o pedido no gateway",
            description = "Tokens de teste: `tok_decline` recusa por saldo, `tok_fraud` recusa por fraude, "
                    + "qualquer outro aprova.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento processado (aprovado ou recusado)"),
            @ApiResponse(responseCode = "409", description = "Pedido nao esta mais pendente")
    })
    public ResponseEntity<OrderDtos.OrderResponse> pay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDtos.PayOrderRequest request) {

        Order order = payOrder.pay(new PayOrderUseCase.Command(orderId, user.id(), request.paymentToken()));
        return ResponseEntity.ok(OrderDtos.OrderResponse.from(order));
    }

    @PostMapping("/{orderId}/cancellation")
    @Operation(summary = "Cancela um pedido pendente e devolve o estoque")
    public ResponseEntity<OrderDtos.OrderResponse> cancel(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderDtos.CancelOrderRequest request) {

        String reason = request == null ? "cancelado pelo cliente" : request.reasonOrDefault();
        Order order = cancelOrder.cancel(new CancelOrderUseCase.Command(orderId, user.id(), reason));
        return ResponseEntity.ok(OrderDtos.OrderResponse.from(order));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Consulta um pedido",
            description = "Cliente so enxerga os proprios pedidos; ADMIN enxerga todos.")
    public ResponseEntity<OrderDtos.OrderResponse> findById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID orderId) {

        return ResponseEntity.ok(OrderDtos.OrderResponse.from(
                findOrder.findById(orderId, user.asRequester())));
    }

    @GetMapping
    @Operation(summary = "Lista pedidos paginados")
    public ResponseEntity<OrderDtos.PageResponse<OrderDtos.OrderResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        var result = findOrder.list(user.asRequester(), PageQuery.of(page, size));
        return ResponseEntity.ok(OrderDtos.PageResponse.from(result, OrderDtos.OrderResponse::from));
    }
}
