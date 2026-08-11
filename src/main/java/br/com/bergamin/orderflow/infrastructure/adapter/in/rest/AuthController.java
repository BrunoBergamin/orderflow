package br.com.bergamin.orderflow.infrastructure.adapter.in.rest;

import br.com.bergamin.orderflow.infrastructure.adapter.in.rest.dto.AuthDtos;
import br.com.bergamin.orderflow.infrastructure.security.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticacao", description = "Emissao de token JWT")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve um token JWT",
            description = "Use o token no cabecalho Authorization: Bearer <token>.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido"),
            @ApiResponse(responseCode = "401", description = "Credenciais invalidas", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<AuthDtos.LoginResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        var token = authenticationService.login(request.email(), request.password());
        return ResponseEntity.ok(AuthDtos.LoginResponse.from(token));
    }
}
