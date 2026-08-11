package br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto;

import br.com.bergamin.orderflow.domain.model.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Contratos HTTP do recurso de produtos. */
public final class ProductDtos {

    private ProductDtos() {
    }

    public record CreateProductRequest(
            @NotBlank(message = "sku e obrigatorio")
            @Size(max = 60, message = "sku deve ter no maximo 60 caracteres") String sku,

            @NotBlank(message = "name e obrigatorio")
            @Size(max = 255, message = "name deve ter no maximo 255 caracteres") String name,

            @NotNull(message = "price e obrigatorio")
            @DecimalMin(value = "0.01", message = "price deve ser maior que zero")
            @Digits(integer = 17, fraction = 2, message = "price aceita no maximo 2 casas decimais")
            BigDecimal price,

            @Min(value = 0, message = "stockQuantity nao pode ser negativo") int stockQuantity) {
    }

    public record ProductResponse(UUID id, String sku, String name, BigDecimal price, int stockQuantity) {

        public static ProductResponse from(Product product) {
            return new ProductResponse(product.getId(), product.getSku(), product.getName(),
                    product.getPrice().amount(), product.getStockQuantity());
        }
    }
}
