package br.com.aloseguro.service;

import br.com.aloseguro.dto.ConsultaDtos.ConsultaResponse;
import br.com.aloseguro.model.CentralOficial;
import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.model.TipoLinha;
import br.com.aloseguro.repository.CentralOficialRepository;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.util.NumeroTelefone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Calcula o score de risco (0 a 100) de um número consultado.
 *
 * Variáveis usadas (versão 1):
 *  - denúncias nos últimos 30 dias ............ até 30 pontos
 *  - pessoas diferentes que denunciaram ........ até 30 pontos
 *  - celular se passando por central bancária .. 15 pontos
 *  - pico: 3 ou mais denúncias em 24 horas ..... 15 pontos
 *  - golpista pediu senha, código ou Pix ....... 10 pontos
 *
 * Caso especial: se o número for de uma central oficial, a resposta não é
 * "seguro" e sim o alerta de spoofing (número verdadeiro na tela, mas
 * possivelmente falsificado pelo golpista).
 */
@Service
public class ScoreRiscoService {

    static final int PONTOS_POR_DENUNCIA_RECENTE = 6;
    static final int PONTOS_POR_PESSOA = 6;
    static final int LIMITE_CONTAGEM = 5;
    static final int PONTOS_CELULAR = 15;
    static final int PONTOS_PICO = 15;
    static final int MINIMO_PARA_PICO = 3;
    static final int PONTOS_ABORDAGEM_CRITICA = 10;

    private final NumeroSuspeitoRepository numeros;
    private final DenunciaRepository denuncias;
    private final CentralOficialRepository centrais;

    public ScoreRiscoService(NumeroSuspeitoRepository numeros, DenunciaRepository denuncias,
                             CentralOficialRepository centrais) {
        this.numeros = numeros;
        this.denuncias = denuncias;
        this.centrais = centrais;
    }

    @Transactional(readOnly = true)
    public ConsultaResponse consultar(String entrada) {
        String numero = NumeroTelefone.normalizar(entrada);
        TipoLinha tipo = NumeroTelefone.tipo(numero);
        String formatado = NumeroTelefone.formatar(numero);

        Contagens c = numeros.findByNumero(numero).map(this::contar).orElse(Contagens.VAZIA);
        Optional<CentralOficial> central = centrais.findByNumero(numero);

        if (central.isPresent()) {
            return respostaCentralOficial(numero, formatado, tipo, central.get(), c);
        }
        if (c.total() == 0) {
            return respostaSemRegistros(numero, formatado, tipo);
        }
        return respostaComScore(numero, formatado, tipo, c);
    }

    private Contagens contar(NumeroSuspeito n) {
        Instant agora = Instant.now();
        return new Contagens(
                denuncias.countByNumero(n),
                denuncias.countByNumeroAndCriadoEmAfter(n, agora.minus(Duration.ofDays(30))),
                denuncias.countByNumeroAndCriadoEmAfter(n, agora.minus(Duration.ofHours(24))),
                denuncias.contarUsuariosDistintos(n),
                denuncias.countByNumeroAndTipoAbordagemIn(n, TipoAbordagem.CRITICAS));
    }

    private ConsultaResponse respostaComScore(String numero, String formatado, TipoLinha tipo, Contagens c) {
        List<String> motivos = new ArrayList<>();
        int score = 0;

        if (c.ultimos30Dias() > 0) {
            score += Math.min(c.ultimos30Dias(), LIMITE_CONTAGEM) * PONTOS_POR_DENUNCIA_RECENTE;
            motivos.add(plural(c.ultimos30Dias(), "denúncia", "denúncias") + " nos últimos 30 dias");
        }

        score += Math.min(c.pessoas(), LIMITE_CONTAGEM) * PONTOS_POR_PESSOA;
        motivos.add("Denunciado por " + plural(c.pessoas(), "pessoa diferente", "pessoas diferentes"));

        if (tipo == TipoLinha.CELULAR) {
            score += PONTOS_CELULAR;
            motivos.add("Ligação de celular se apresentando como banco: centrais bancárias usam 0800, 4004/3003 ou telefone fixo");
        }

        if (c.ultimas24h() >= MINIMO_PARA_PICO) {
            score += PONTOS_PICO;
            motivos.add(plural(c.ultimas24h(), "denúncia", "denúncias") + " nas últimas 24 horas (pico de atividade)");
        }

        if (c.criticas() > 0) {
            score += PONTOS_ABORDAGEM_CRITICA;
            motivos.add("Pediu senha, código, Pix ou cartão em " + plural(c.criticas(), "relato", "relatos")
                    + " — nenhum banco faz isso por telefone");
        }

        score = Math.min(score, 100);
        NivelRisco nivel = score <= 30 ? NivelRisco.BAIXO : score <= 70 ? NivelRisco.ATENCAO : NivelRisco.ALTO;

        String mensagem = switch (nivel) {
            case ALTO -> "Alto risco de golpe. Desligue e não informe nenhum dado. Se precisar falar com o banco, ligue você mesmo para o número do cartão.";
            case ATENCAO -> "Este número já foi denunciado. Não passe senha, código ou faça transferências. Na dúvida, desligue e ligue para o banco.";
            default -> "Há poucas denúncias para este número. Fique atento: banco nenhum pede senha, código ou transferência por telefone.";
        };

        return new ConsultaResponse(numero, formatado, tipo, tipo.getDescricao(), nivel, score, mensagem, motivos,
                c.total(), c.ultimos30Dias(), c.pessoas(), null);
    }

    private ConsultaResponse respostaCentralOficial(String numero, String formatado, TipoLinha tipo,
                                                   CentralOficial central, Contagens c) {
        List<String> motivos = new ArrayList<>();
        motivos.add("Número cadastrado como central oficial do " + central.getBanco());
        if (c.total() > 0) {
            motivos.add(plural(c.total(), "pessoa relatou", "pessoas relataram")
                    + " golpe com este número aparecendo na tela — indício de falsificação (spoofing)");
        }

        String mensagem = "Este é um número oficial do " + central.getBanco() + ", mas golpistas conseguem "
                + "falsificar o número que aparece na tela. Nenhum banco pede senha, token, código ou "
                + "transferência por telefone. Na dúvida, desligue e ligue você mesmo para o número do cartão.";

        return new ConsultaResponse(numero, formatado, tipo, tipo.getDescricao(), NivelRisco.CENTRAL_OFICIAL, 0,
                mensagem, motivos, c.total(), c.ultimos30Dias(), c.pessoas(), central.getBanco());
    }

    private ConsultaResponse respostaSemRegistros(String numero, String formatado, TipoLinha tipo) {
        List<String> motivos = new ArrayList<>();
        motivos.add("Nenhuma denúncia registrada para este número");
        if (tipo == TipoLinha.CELULAR) {
            motivos.add("Observação: centrais bancárias não costumam ligar de números de celular");
        }

        String mensagem = "Nenhuma denúncia até agora, o que não garante que o número seja seguro. "
                + "Se pedirem senha, código ou transferência, desligue e denuncie aqui.";

        return new ConsultaResponse(numero, formatado, tipo, tipo.getDescricao(), NivelRisco.SEM_REGISTROS, 0,
                mensagem, motivos, 0, 0, 0, null);
    }

    private static String plural(long quantidade, String singular, String plural) {
        return quantidade + " " + (quantidade == 1 ? singular : plural);
    }

    private record Contagens(long total, long ultimos30Dias, long ultimas24h, long pessoas, long criticas) {
        static final Contagens VAZIA = new Contagens(0, 0, 0, 0, 0);
    }
}
