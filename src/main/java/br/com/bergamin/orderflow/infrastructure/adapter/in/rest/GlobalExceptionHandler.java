package br.com.bergamin.orderflow.infrastructure.adapter.in.rest;

import br.com.bergamin.orderflow.domain.exception.AccessDeniedToOrderException;
import br.com.bergamin.orderflow.domain.exception.DuplicateRequestException;
import br.com.bergamin.orderflow.domain.exception.DuplicateSkuException;
import br.com.bergamin.orderflow.domain.exception.InsufficientStockException;
import br.com.bergamin.orderflow.domain.exception.InvalidOrderStateException;
import br.com.bergamin.orderflow.domain.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converte excecoes em respostas RFC 7807 ({@code application/problem+json}).
 *
 * <p>Um so lugar decide o status HTTP de cada falha. Os casos de uso lancam excecao de
 * negocio e nao sabem o que e "409". Se a API virar gRPC amanha, muda apenas esta classe.</p>
 *
 * <p>Erros inesperados (500) nunca devolvem stack trace: o cliente recebe um
 * {@code errorId}, o mesmo id vai para o log, e o suporte cruza os dois. Vazar mensagem de
 * excecao interna e entregar mapa do sistema para quem esta sondando.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://orderflow.dev/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Recurso nao encontrado", e.getMessage(), "recurso-nao-encontrado");
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException e) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "Estoque insuficiente", e.getMessage(), "estoque-insuficiente");
        problem.setProperty("sku", e.getSku());
        problem.setProperty("requested", e.getRequested());
        problem.setProperty("available", e.getAvailable());
        return problem;
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidState(InvalidOrderStateException e) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT,
                "Transicao de status invalida", e.getMessage(), "status-invalido");
        problem.setProperty("currentStatus", e.getCurrent().name());
        problem.setProperty("targetStatus", e.getTarget().name());
        return problem;
    }

    @ExceptionHandler(AccessDeniedToOrderException.class)
    public ProblemDetail handleOrderAccessDenied(AccessDeniedToOrderException e) {
        // Mensagem generica: confirmar "existe, mas nao e seu" ja entrega informacao.
        return problem(HttpStatus.FORBIDDEN, "Acesso negado",
                "Voce nao tem acesso a este pedido.", "acesso-negado");
    }

    @ExceptionHandler({DuplicateRequestException.class, DuplicateSkuException.class})
    public ProblemDetail handleConflict(RuntimeException e) {
        return problem(HttpStatus.CONFLICT, "Conflito", e.getMessage(), "conflito");
    }

    /**
     * Lock otimista perdido: outra requisicao alterou o mesmo registro primeiro.
     *
     * <p>409 e a resposta correta. O pedido nao aconteceu, e repetir a chamada tende a
     * funcionar. E este o caminho que impede a venda de estoque que nao existe.</p>
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException e) {
        log.debug("conflito de concorrencia: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito de concorrencia",
                "O registro foi alterado por outra requisicao. Tente novamente.", "concorrencia");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("violacao de integridade: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Conflito de dados",
                "A operacao viola uma restricao de integridade.", "integridade");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "valor invalido" : error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "Um ou mais campos falharam na validacao.", "validacao");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage(), "validacao");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage(), "requisicao-invalida");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Credenciais invalidas", e.getMessage(), "credenciais-invalidas");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        String errorId = UUID.randomUUID().toString();
        log.error("erro inesperado [errorId={}]", errorId, e);

        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Informe o errorId ao suporte.", "erro-interno");
        problem.setProperty("errorId", errorId);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("timestamp", Instant.now().toString());
        extras.forEach(problem::setProperty);
        return problem;
    }
}
