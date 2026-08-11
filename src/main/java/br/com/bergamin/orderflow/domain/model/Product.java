package br.com.bergamin.orderflow.domain.model;

import br.com.bergamin.orderflow.domain.exception.InsufficientStockException;

import java.util.Objects;
import java.util.UUID;

/**
 * Produto e seu estoque disponivel.
 *
 * <p>A reserva de estoque e um metodo do agregado, nao um {@code UPDATE} espalhado no
 * servico. O campo {@code version} alimenta o lock otimista do JPA: dois pedidos
 * concorrentes na ultima unidade fazem o segundo falhar em vez de vender estoque
 * negativo.</p>
 */
public class Product {

    private final UUID id;
    private final String sku;
    private final String name;
    private final Money price;
    private int stockQuantity;
    private final Long version;

    private Product(UUID id, String sku, String name, Money price, int stockQuantity, Long version) {
        this.id = Objects.requireNonNull(id, "id e obrigatorio");
        this.sku = Objects.requireNonNull(sku, "sku e obrigatorio");
        this.name = Objects.requireNonNull(name, "name e obrigatorio");
        this.price = Objects.requireNonNull(price, "price e obrigatorio");
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("estoque nao pode ser negativo: " + stockQuantity);
        }
        this.stockQuantity = stockQuantity;
        this.version = version;
    }

    /** Cria um produto novo (ainda nao persistido). */
    public static Product create(String sku, String name, Money price, int stockQuantity) {
        return new Product(UUID.randomUUID(), sku, name, price, stockQuantity, null);
    }

    /** Reidrata um produto vindo do banco, preservando a versao do lock otimista. */
    public static Product restore(UUID id, String sku, String name, Money price, int stockQuantity, Long version) {
        return new Product(id, sku, name, price, stockQuantity, version);
    }

    /** Baixa estoque para um pedido. Lanca se nao houver quantidade suficiente. */
    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        if (quantity > stockQuantity) {
            throw new InsufficientStockException(sku, quantity, stockQuantity);
        }
        this.stockQuantity -= quantity;
    }

    /** Devolve estoque quando o pagamento falha ou o pedido e cancelado. */
    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantidade deve ser maior que zero");
        }
        this.stockQuantity += quantity;
    }

    public boolean hasStockFor(int quantity) {
        return stockQuantity >= quantity;
    }

    public UUID getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public Money getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public Long getVersion() {
        return version;
    }
}
