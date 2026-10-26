package br.com.aloseguro.controller;

import br.com.aloseguro.dto.AuthDtos.CadastroRequest;
import br.com.aloseguro.dto.AuthDtos.LoginRequest;
import br.com.aloseguro.dto.AuthDtos.TokenResponse;
import br.com.aloseguro.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** POST /api/auth/cadastro — cria a conta e já devolve o token. */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse cadastrar(@Valid @RequestBody CadastroRequest req) {
        return authService.cadastrar(req);
    }

    /** POST /api/auth/login — confere e-mail e senha e devolve o token JWT. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
