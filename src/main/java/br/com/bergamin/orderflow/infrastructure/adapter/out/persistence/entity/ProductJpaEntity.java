package br.com.bergamin.orderflow.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tabela {@code products}.
 *
 * <p>{@code @Version} liga o lock otimista do Hibernate. E o que impede venda de estoque
 * negativo quando duas requisicoes disputam a ultima unidade: o UPDATE do segundo carrega
 * {@code WHERE version = ?}, nao acerta nenhuma linha e estoura
 * {@code OptimisticLockingFailureException}, que o handler traduz em HTTP 409.</p>
 */
@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ProductJpaEntity() {
        // exigido pelo JPA
    }

    public ProductJpaEntity(UUID id, String sku, String name, BigDecimal price, int stockQuantity) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
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

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Long getVersion() {
        return version;
    }
}
