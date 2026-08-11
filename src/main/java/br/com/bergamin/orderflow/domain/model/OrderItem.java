package br.com.bergamin.orderflow.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Linha de um pedido.
 *
 * <p>O preco unitario e copiado do produto no momento da compra (snapshot). Se o produto
 * mudar de preco depois, o historico do pedido continua correto -- requisito basico de
 * qualquer sistema que emite nota fiscal.</p>
 */
public record OrderItem(UUID productId, String sku, String description, int quantity, Money unitPrice) {

    public OrderItem {
        Objects.requireNonNull(productId, "productId e obrigatorio");
        Objects.requireNonNull(sku, "sku e obrigatorio");
        Objects.requireNonNull(unitPrice, "unitPrice e obrigatorio");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero, recebido: " + quantity);
        }
    }

    public static OrderItem fromProduct(Product product, int quantity) {
        return new OrderItem(product.getId(), product.getSku(), product.getName(), quantity, product.getPrice());
    }

    public Money subtotal() {
        return unitPrice.multiply(quantity);
    }
}
