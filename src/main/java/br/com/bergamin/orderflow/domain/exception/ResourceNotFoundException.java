package br.com.bergamin.orderflow.domain.exception;

/** Recurso inexistente. Vira HTTP 404. */
public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super("%s nao encontrado: %s".formatted(resource, identifier));
    }
}
