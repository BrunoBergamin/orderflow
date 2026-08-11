package br.com.bergamin.orderflow.domain.exception;

/** Estoque insuficiente para atender a quantidade pedida. Vira HTTP 422. */
public class InsufficientStockException extends DomainException {

    private final String sku;
    private final int requested;
    private final int available;

    public InsufficientStockException(String sku, int requested, int available) {
        super("estoque insuficiente para o SKU %s: solicitado %d, disponivel %d"
                .formatted(sku, requested, available));
        this.sku = sku;
        this.requested = requested;
        this.available = available;
    }

    public String getSku() {
        return sku;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
