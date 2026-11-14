package br.com.aloseguro.controller;

import br.com.aloseguro.dto.AuthDtos.AtualizarPerfilRequest;
import br.com.aloseguro.dto.AuthDtos.PerfilResponse;
import br.com.aloseguro.service.ContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conta do usuário logado (exige token).
 */
@RestController
@RequestMapping("/api/conta")
public class ContaController {

    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @GetMapping
    public PerfilResponse perfil(@AuthenticationPrincipal UserDetails usuario) {
        return contaService.perfil(usuario.getUsername());
    }

    @PutMapping
    public PerfilResponse alterarNome(@AuthenticationPrincipal UserDetails usuario,
                                      @Valid @RequestBody AtualizarPerfilRequest req) {
        return contaService.alterarNome(usuario.getUsername(), req);
    }

    /** DELETE /api/conta — exclui a conta e todas as denúncias da pessoa. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UserDetails usuario) {
        contaService.excluirConta(usuario.getUsername());
    }
}
