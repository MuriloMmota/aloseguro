package br.com.aloseguro.service;

import br.com.aloseguro.dto.ConsultaDtos.RankingItemResponse;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaRequest;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaResponse;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaUpdateRequest;
import br.com.aloseguro.exception.NegocioException;
import br.com.aloseguro.ia.ClassificadorRelatos;
import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.repository.UsuarioRepository;
import br.com.aloseguro.util.NumeroTelefone;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Regras das denúncias: criar, consultar, editar e excluir (CRUD) e o ranking.
 */
@Service
public class DenunciaService {

    private final DenunciaRepository denuncias;
    private final NumeroSuspeitoRepository numeros;
    private final UsuarioRepository usuarios;
    private final ClassificadorRelatos classificador;

    public DenunciaService(DenunciaRepository denuncias, NumeroSuspeitoRepository numeros,
                           UsuarioRepository usuarios, ClassificadorRelatos classificador) {
        this.denuncias = denuncias;
        this.numeros = numeros;
        this.usuarios = usuarios;
        this.classificador = classificador;
    }

    @Transactional
    public DenunciaResponse registrar(String emailUsuario, DenunciaRequest req) {
        Usuario usuario = buscarUsuario(emailUsuario);
        String digitos = NumeroTelefone.normalizar(req.numero());

        // Cria o número na primeira denúncia; nas próximas, reaproveita
        NumeroSuspeito numero = numeros.findByNumero(digitos)
                .orElseGet(() -> numeros.save(new NumeroSuspeito(
                        digitos, NumeroTelefone.ddd(digitos), NumeroTelefone.tipo(digitos))));

        // Evita que uma mesma pessoa infle o score denunciando várias vezes seguidas
        if (denuncias.existsByUsuarioAndNumeroAndCriadoEmAfter(usuario, numero, Instant.now().minus(Duration.ofHours(24)))) {
            throw new NegocioException(HttpStatus.CONFLICT, "Você já denunciou este número nas últimas 24 horas.");
        }

        Denuncia denuncia = new Denuncia(numero, usuario, textoOuNulo(req.bancoAlegado()), req.tipoAbordagem(),
                textoOuNulo(req.descricao()), req.dataHoraLigacao());
        classificar(denuncia);
        return DenunciaResponse.de(denuncias.save(denuncia));
    }

    @Transactional(readOnly = true)
    public DenunciaResponse buscar(String emailUsuario, Long id) {
        return DenunciaResponse.de(buscarDoDono(emailUsuario, id));
    }

    @Transactional
    public DenunciaResponse atualizar(String emailUsuario, Long id, DenunciaUpdateRequest req) {
        Denuncia denuncia = buscarDoDono(emailUsuario, id);
        denuncia.atualizar(textoOuNulo(req.bancoAlegado()), req.tipoAbordagem(), textoOuNulo(req.descricao()),
                req.dataHoraLigacao());
        classificar(denuncia);   // o texto mudou: a IA classifica de novo
        return DenunciaResponse.de(denuncia);
    }

    @Transactional
    public void excluir(String emailUsuario, Long id) {
        denuncias.delete(buscarDoDono(emailUsuario, id));
    }

    @Transactional(readOnly = true)
    public List<DenunciaResponse> minhas(String emailUsuario) {
        return denuncias.buscarDoUsuario(buscarUsuario(emailUsuario)).stream()
                .map(DenunciaResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RankingItemResponse> ranking(int dias, int limite) {
        int diasValidos = Math.max(1, Math.min(dias, 365));
        int limiteValido = Math.max(1, Math.min(limite, 50));
        Instant desde = Instant.now().minus(Duration.ofDays(diasValidos));

        return denuncias.ranking(desde, PageRequest.of(0, limiteValido)).stream()
                .map(r -> new RankingItemResponse(
                        r.getNumero(),
                        NumeroTelefone.formatar(r.getNumero()),
                        r.getTotal(),
                        r.getUltima()))
                .toList();
    }

    // ------------------------------------------------------------ auxiliares

    private void classificar(Denuncia denuncia) {
        ClassificadorRelatos.Classificacao c = classificador.classificar(denuncia.getDescricao(), denuncia.getTipoAbordagem());
        denuncia.definirTags(c.tags(), c.origem());
    }

    /** Só o autor pode ver, editar ou excluir a própria denúncia. */
    private Denuncia buscarDoDono(String emailUsuario, Long id) {
        Usuario usuario = buscarUsuario(emailUsuario);
        Denuncia denuncia = denuncias.findById(id)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Denúncia não encontrada."));
        if (!Objects.equals(denuncia.getUsuario().getId(), usuario.getId())) {
            throw new NegocioException(HttpStatus.FORBIDDEN, "Você só pode alterar as suas próprias denúncias.");
        }
        return denuncia;
    }

    private Usuario buscarUsuario(String email) {
        return usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
    }

    private static String textoOuNulo(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
