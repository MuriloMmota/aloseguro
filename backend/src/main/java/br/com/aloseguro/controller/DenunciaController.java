package br.com.aloseguro.controller;

import br.com.aloseguro.dto.DenunciaDtos.DenunciaRequest;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaResponse;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaUpdateRequest;
import br.com.aloseguro.service.DenunciaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de denúncias. Todas as rotas exigem "Authorization: Bearer <token>".
 */
@RestController
@RequestMapping("/api/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;

    public DenunciaController(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    /** POST /api/denuncias — registra uma denúncia; a IA classifica o comentário. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DenunciaResponse registrar(@AuthenticationPrincipal UserDetails usuario,
                                      @Valid @RequestBody DenunciaRequest req) {
        return denunciaService.registrar(usuario.getUsername(), req);
    }

    /** GET /api/denuncias/minhas — histórico do usuário logado. */
    @GetMapping("/minhas")
    public List<DenunciaResponse> minhas(@AuthenticationPrincipal UserDetails usuario) {
        return denunciaService.minhas(usuario.getUsername());
    }

    /** GET /api/denuncias/{id} — uma denúncia do próprio usuário. */
    @GetMapping("/{id}")
    public DenunciaResponse buscar(@AuthenticationPrincipal UserDetails usuario, @PathVariable Long id) {
        return denunciaService.buscar(usuario.getUsername(), id);
    }

    /** PUT /api/denuncias/{id} — edita a própria denúncia (a IA classifica de novo). */
    @PutMapping("/{id}")
    public DenunciaResponse atualizar(@AuthenticationPrincipal UserDetails usuario, @PathVariable Long id,
                                      @Valid @RequestBody DenunciaUpdateRequest req) {
        return denunciaService.atualizar(usuario.getUsername(), id, req);
    }

    /** DELETE /api/denuncias/{id} — exclui a própria denúncia. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@AuthenticationPrincipal UserDetails usuario, @PathVariable Long id) {
        denunciaService.excluir(usuario.getUsername(), id);
    }
}
