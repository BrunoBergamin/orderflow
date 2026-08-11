package br.com.bergamin.orderflow.domain.exception;

/** Ja existe produto com o SKU informado. Vira HTTP 409. */
public class DuplicateSkuException extends DomainException {

    public DuplicateSkuException(String sku) {
        super("ja existe um produto com o SKU " + sku);
    }
}
