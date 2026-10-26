package br.com.aloseguro.controller;

import br.com.aloseguro.dto.DenunciaDtos.DenunciaRequest;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaResponse;
import br.com.aloseguro.service.DenunciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Rotas protegidas: só funcionam com "Authorization: Bearer <token>".
 */
@RestController
@RequestMapping("/api/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;

    public DenunciaController(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    /** POST /api/denuncias — registra uma denúncia do usuário logado. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DenunciaResponse registrar(@AuthenticationPrincipal UserDetails usuario,
                                      @Valid @RequestBody DenunciaRequest req) {
        return denunciaService.registrar(usuario.getUsername(), req);
    }

    /** GET /api/denuncias/minhas — histórico de denúncias do usuário logado. */
    @GetMapping("/minhas")
    public List<DenunciaResponse> minhas(@AuthenticationPrincipal UserDetails usuario) {
        return denunciaService.minhas(usuario.getUsername());
    }
}
