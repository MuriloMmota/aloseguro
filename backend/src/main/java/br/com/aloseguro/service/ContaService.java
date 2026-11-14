package br.com.aloseguro.service;

import br.com.aloseguro.dto.AuthDtos.AtualizarPerfilRequest;
import br.com.aloseguro.dto.AuthDtos.PerfilResponse;
import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados da conta do usuário logado: ver, alterar o nome e excluir a conta.
 * A exclusão apaga também as denúncias da pessoa (direito de exclusão da LGPD).
 */
@Service
public class ContaService {

    private final UsuarioRepository usuarios;
    private final DenunciaRepository denuncias;

    public ContaService(UsuarioRepository usuarios, DenunciaRepository denuncias) {
        this.usuarios = usuarios;
        this.denuncias = denuncias;
    }

    @Transactional(readOnly = true)
    public PerfilResponse perfil(String email) {
        Usuario u = buscar(email);
        return perfilDe(u);
    }

    @Transactional
    public PerfilResponse alterarNome(String email, AtualizarPerfilRequest req) {
        Usuario u = buscar(email);
        u.alterarNome(req.nome().trim());
        return perfilDe(u);
    }

    @Transactional
    public void excluirConta(String email) {
        Usuario u = buscar(email);
        denuncias.apagarDoUsuario(u);   // primeiro as denúncias (chave estrangeira), depois a conta
        usuarios.delete(u);
    }

    private PerfilResponse perfilDe(Usuario u) {
        long total = denuncias.buscarDoUsuario(u).size();
        return new PerfilResponse(u.getId(), u.getNome(), u.getEmail(), u.getCriadoEm(), total);
    }

    private Usuario buscar(String email) {
        return usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
    }
}
