package br.com.aloseguro.service;

import br.com.aloseguro.dto.ConsultaDtos.RankingItemResponse;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaRequest;
import br.com.aloseguro.dto.DenunciaDtos.DenunciaResponse;
import br.com.aloseguro.exception.NegocioException;
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

@Service
public class DenunciaService {

    private final DenunciaRepository denuncias;
    private final NumeroSuspeitoRepository numeros;
    private final UsuarioRepository usuarios;

    public DenunciaService(DenunciaRepository denuncias, NumeroSuspeitoRepository numeros,
                           UsuarioRepository usuarios) {
        this.denuncias = denuncias;
        this.numeros = numeros;
        this.usuarios = usuarios;
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

        Denuncia denuncia = denuncias.save(new Denuncia(
                numero,
                usuario,
                textoOuNulo(req.bancoAlegado()),
                req.tipoAbordagem(),
                textoOuNulo(req.descricao()),
                req.dataHoraLigacao()));

        return DenunciaResponse.de(denuncia);
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

    private Usuario buscarUsuario(String email) {
        return usuarios.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
    }

    private static String textoOuNulo(String texto) {
        return (texto == null || texto.isBlank()) ? null : texto.trim();
    }
}
