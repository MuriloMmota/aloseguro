package br.com.aloseguro.service;

import br.com.aloseguro.dto.ConsultaDtos.ConsultaResponse;
import br.com.aloseguro.dto.ConsultaDtos.DefinicaoFatorResponse;
import br.com.aloseguro.dto.ConsultaDtos.FaixaResponse;
import br.com.aloseguro.dto.ConsultaDtos.FatorResponse;
import br.com.aloseguro.dto.ConsultaDtos.MotorResponse;
import br.com.aloseguro.dto.ConsultaDtos.RelatoResponse;
import br.com.aloseguro.dto.ConsultaDtos.TagResponse;
import br.com.aloseguro.dto.ConsultaDtos.TopicoResponse;
import br.com.aloseguro.model.CentralOficial;
import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoLinha;
import br.com.aloseguro.motor.MotorRisco;
import br.com.aloseguro.repository.CentralOficialRepository;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.util.NumeroTelefone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Busca os dados de um número no banco, entrega ao MotorRisco e monta a resposta da consulta.
 */
@Service
public class RiscoService {

    private final NumeroSuspeitoRepository numeros;
    private final DenunciaRepository denuncias;
    private final CentralOficialRepository centrais;
    private final MotorRisco motor;

    public RiscoService(NumeroSuspeitoRepository numeros, DenunciaRepository denuncias,
                        CentralOficialRepository centrais, MotorRisco motor) {
        this.numeros = numeros;
        this.denuncias = denuncias;
        this.centrais = centrais;
        this.motor = motor;
    }

    @Transactional(readOnly = true)
    public ConsultaResponse consultar(String entrada) {
        String numero = NumeroTelefone.normalizar(entrada);
        TipoLinha tipo = NumeroTelefone.tipo(numero);
        Instant agora = Instant.now();

        Optional<CentralOficial> central = centrais.findByNumero(numero);
        List<Denuncia> lista = numeros.findByNumero(numero).map(denuncias::buscarDoNumero).orElse(List.of());
        long vizinhos = prefixoDoBloco(numero, tipo)
                .map(prefixo -> denuncias.contarVizinhosDenunciados(prefixo + "%", numero))
                .orElse(0L);

        List<MotorRisco.DadosDenuncia> dados = lista.stream()
                .map(d -> new MotorRisco.DadosDenuncia(d.getCriadoEm(), d.getUsuario().getId(),
                        d.getUsuario().getCriadoEm(), d.getTags()))
                .toList();
        MotorRisco.Resultado resultado = motor.calcular(dados, tipo, vizinhos, central.isPresent(), agora);

        List<FatorResponse> fatores = resultado.fatores().stream()
                .map(f -> new FatorResponse(f.codigo(), f.nome(), f.explicacao(), f.pontos(), f.maximo()))
                .toList();

        long ultimos30Dias = lista.stream()
                .filter(d -> d.getCriadoEm().isAfter(agora.minus(Duration.ofDays(30))))
                .count();
        long pessoas = lista.stream().map(d -> d.getUsuario().getId()).distinct().count();
        Instant ultima = lista.isEmpty() ? null : lista.get(0).getCriadoEm();

        return new ConsultaResponse(
                numero,
                NumeroTelefone.formatar(numero),
                tipo,
                tipo.getDescricao(),
                resultado.nivel(),
                resultado.score(),
                mensagem(resultado.nivel(), central.map(CentralOficial::getBanco).orElse(null)),
                resultado.confianca(),
                fatores,
                topicos(lista),
                relatosRecentes(lista),
                lista.size(),
                ultimos30Dias,
                pessoas,
                ultima,
                central.map(CentralOficial::getBanco).orElse(null));
    }

    /** Explicação do motor para a página "Como calculamos o risco". */
    public MotorResponse explicarMotor() {
        List<DefinicaoFatorResponse> fatores = motor.definicoes().stream()
                .map(d -> new DefinicaoFatorResponse(d.codigo(), d.nome(), d.maximo(), d.comoFunciona()))
                .toList();
        List<FaixaResponse> faixas = List.of(
                new FaixaResponse("SEM_REGISTROS", "—", "Nenhuma denúncia e nenhum vizinho suspeito. Não garante que o número seja seguro."),
                new FaixaResponse("BAIXO", "0 a " + (MotorRisco.LIMITE_BAIXO - 1), "Poucas denúncias, antigas ou de pouca gravidade."),
                new FaixaResponse("ATENCAO", MotorRisco.LIMITE_BAIXO + " a " + (MotorRisco.LIMITE_ALTO - 1),
                        "Denúncias relevantes, ou número novo dentro de um bloco muito denunciado."),
                new FaixaResponse("ALTO", MotorRisco.LIMITE_ALTO + " a 100", "Vários sinais de golpe ao mesmo tempo."),
                new FaixaResponse("CENTRAL_OFICIAL", "—",
                        "Número oficial de banco. Como pode ser falsificado (spoofing), a resposta é sempre um alerta, nunca \"seguro\"."));
        List<TagResponse> tags = Arrays.stream(TagRelato.values())
                .map(t -> new TagResponse(t.name(), t.getDescricao(), t.getGravidade()))
                .toList();
        String classificacao = "O comentário de cada denúncia é enviado a um modelo de linguagem (LLM), que devolve os tópicos "
                + "encontrados, escolhidos apenas desta lista. Se a IA não responder, um classificador por palavras-chave assume.";
        return new MotorResponse("2.0", fatores, faixas, tags, classificacao);
    }

    // ------------------------------------------------------------ auxiliares

    /** Prefixo do "bloco" de numeração: DDD + 5 dígitos no celular, DDD + 4 no fixo. */
    static Optional<String> prefixoDoBloco(String numero, TipoLinha tipo) {
        return switch (tipo) {
            case CELULAR -> Optional.of(numero.substring(0, 7));
            case FIXO -> Optional.of(numero.substring(0, 6));
            default -> Optional.empty();
        };
    }

    private List<TopicoResponse> topicos(List<Denuncia> lista) {
        Map<TagRelato, Long> contagem = new EnumMap<>(TagRelato.class);
        for (Denuncia d : lista) {
            for (TagRelato tag : d.getTags()) {
                contagem.merge(tag, 1L, Long::sum);
            }
        }
        return contagem.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<TagRelato, Long> e) -> e.getValue()).reversed()
                        .thenComparing(e -> -e.getKey().getGravidade()))
                .map(e -> new TopicoResponse(e.getKey().name(), e.getKey().getDescricao(), e.getKey().getGravidade(), e.getValue()))
                .toList();
    }

    private List<RelatoResponse> relatosRecentes(List<Denuncia> lista) {
        return lista.stream()
                .filter(d -> d.getDescricao() != null && !d.getDescricao().isBlank())
                .limit(3)
                .map(d -> new RelatoResponse(d.getDescricao(),
                        d.getTags().stream().map(TagRelato::getDescricao).toList(),
                        d.getCriadoEm()))
                .toList();
    }

    private static String mensagem(NivelRisco nivel, String bancoOficial) {
        return switch (nivel) {
            case ALTO -> "Alto risco de golpe. Desligue e não informe nenhum dado. Se precisar falar com o banco, "
                    + "ligue você mesmo para o número do cartão.";
            case ATENCAO -> "Atenção: há sinais de golpe ligados a este número. Não passe senha, código nem faça "
                    + "transferências. Na dúvida, desligue e ligue para o banco.";
            case BAIXO -> "Há poucos sinais contra este número. Mesmo assim: nenhum banco pede senha, código "
                    + "ou transferência por telefone.";
            case CENTRAL_OFICIAL -> "Este é um número oficial do " + bancoOficial + ", mas golpistas conseguem falsificar "
                    + "o número que aparece na tela. Nenhum banco pede senha, token, código ou transferência por telefone. "
                    + "Na dúvida, desligue e ligue você mesmo para o número do cartão.";
            case SEM_REGISTROS -> "Nenhuma denúncia até agora, o que não garante que o número seja seguro. "
                    + "Se pedirem senha, código ou transferência, desligue e denuncie aqui.";
        };
    }
}
