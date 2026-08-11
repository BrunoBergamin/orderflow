package br.com.bergamin.orderflow.application.common;

/**
 * Paginacao pedida pelo caso de uso.
 *
 * <p>Tipo proprio em vez de {@code org.springframework.data.domain.Pageable}: as portas
 * ficam livres de Spring Data, e trocar o mecanismo de persistencia nao obriga a mexer
 * na assinatura dos casos de uso.</p>
 */
public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page nao pode ser negativa");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size deve estar entre 1 e " + MAX_SIZE);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }
}
