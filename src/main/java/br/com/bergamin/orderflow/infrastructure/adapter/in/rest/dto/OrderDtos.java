package br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto;

import br.com.bergamin.orderflow.application.common.PagedResult;
import br.com.bergamin.orderflow.domain.model.Order;
import br.com.bergamin.orderflow.domain.model.OrderItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contratos HTTP do recurso de pedidos.
 *
 * <p>DTOs separados do dominio: o corpo da requisicao nunca traz o total nem o status --
 * quem manda preco e o servidor. Aceitar {@code total} do cliente e o caminho mais curto
 * para vender um iPhone por R$ 1,00.</p>
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    @Schema(description = "Itens do pedido. O preco vem do catalogo, nao da requisicao.")
    public record PlaceOrderRequest(
            @NotEmpty(message = "informe ao menos um item")
            @Size(max = 50, message = "no maximo 50 itens por pedido")
            @Valid List<Item> items) {

        public record Item(
                @NotNull(message = "productId e obrigatorio") UUID productId,
                @Min(value = 1, message = "quantidade minima e 1") int quantity) {
        }
    }

    public record PayOrderRequest(
            @Schema(description = "Token do meio de pagamento", example = "tok_visa_4242")
            @NotBlank(message = "paymentToken e obrigatorio") String paymentToken) {
    }

    public record CancelOrderRequest(
            @Size(max = 200, message = "motivo deve ter no maximo 200 caracteres") String reason) {

        public String reasonOrDefault() {
            return reason == null || reason.isBlank() ? "cancelado pelo cliente" : reason;
        }
    }

    public record OrderResponse(
            UUID id,
            UUID customerId,
            String status,
            BigDecimal total,
            List<Item> items,
            String paymentTransactionId,
            String statusReason,
            Instant createdAt,
            Instant updatedAt) {

        public record Item(UUID productId, String sku, String description,
                           int quantity, BigDecimal unitPrice, BigDecimal subtotal) {

            static Item from(OrderItem item) {
                return new Item(item.productId(), item.sku(), item.description(),
                        item.quantity(), item.unitPrice().amount(), item.subtotal().amount());
            }
        }

        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomerId(),
                    order.getStatus().name(),
                    order.total().amount(),
                    order.getItems().stream().map(Item::from).toList(),
                    order.getPaymentTransactionId(),
                    order.getStatusReason(),
                    order.getCreatedAt(),
                    order.getUpdatedAt());
        }
    }

    /** Envelope de paginacao usado por todas as listagens da API. */
    public record PageResponse<T>(List<T> content, int page, int size,
                                  long totalElements, int totalPages, boolean hasNext) {

        public static <D, R> PageResponse<R> from(PagedResult<D> result, java.util.function.Function<D, R> mapper) {
            return new PageResponse<>(
                    result.content().stream().map(mapper).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages(),
                    result.hasNext());
        }
    }
}
