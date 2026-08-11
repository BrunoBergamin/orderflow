package br.com.bergamin.orderflow.domain.exception;

/**
 * Base das violacoes de regra de negocio.
 *
 * <p>Ter um tipo raiz permite ao handler HTTP tratar todas as falhas de dominio de forma
 * uniforme, sem o {@code catch (Exception e)} que engole bug de infraestrutura junto.</p>
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
