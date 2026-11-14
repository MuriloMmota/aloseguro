package br.com.aloseguro.motor;

import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoLinha;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Motor de risco do AlôSeguro (versão 2).
 *
 * O score vai de 0 a 100 e é a soma de 6 fatores. Cada fator tem um máximo e uma
 * explicação, que aparece na tela para o usuário entender de onde veio a nota.
 *
 *  F1 Recência das denúncias ............ até 25  (denúncia recente pesa mais que antiga)
 *  F2 Pessoas diferentes ................ até 25  (contas novas pesam menos)
 *  F3 Gravidade dos relatos ............. até 20  (tópicos identificados pela IA)
 *  F4 Perfil da linha ................... até 10  (celular se passando por banco)
 *  F5 Pico de denúncias ................. até 10  (várias denúncias em 24 horas)
 *  F6 Bloco de números suspeito ......... até 10  (números vizinhos também denunciados)
 *
 * Os fatores F1 e F2 usam a curva 1 - e^(-x/k): cresce rápido nas primeiras denúncias e
 * depois "satura". Assim 50 denúncias não valem 50 vezes mais que 1, e ninguém consegue
 * levar um número ao máximo só com volume.
 *
 * A classe não acessa o banco: recebe os dados prontos. Isso permite testar o cálculo
 * isoladamente (MotorRiscoTest).
 */
@Component
public class MotorRisco {

    // ------------------------------------------------------------ pesos e parâmetros
    public static final int MAX_RECENCIA = 25;
    public static final int MAX_PESSOAS = 25;
    public static final int MAX_GRAVIDADE = 20;
    public static final int MAX_LINHA = 10;
    public static final int MAX_PICO = 10;
    public static final int MAX_VIZINHANCA = 10;

    /** Em quantos dias uma denúncia perde ~63% do peso (decaimento exponencial). */
    public static final double DIAS_DECAIMENTO = 15.0;
    public static final double SATURACAO_RECENCIA = 2.0;
    public static final double SATURACAO_PESSOAS = 2.5;
    /** Quantas denúncias são necessárias para o fator de gravidade valer por inteiro. */
    public static final double DENUNCIAS_PARA_GRAVIDADE_CHEIA = 3.0;
    public static final int DENUNCIAS_PICO = 3;

    public static final double PESO_CONTA_NOVA = 0.3;      // conta com menos de 1 dia
    public static final double PESO_CONTA_RECENTE = 0.7;   // conta com menos de 30 dias
    public static final double PESO_CONTA_ANTIGA = 1.0;

    public static final int LIMITE_BAIXO = 30;   // abaixo disso: risco baixo
    public static final int LIMITE_ALTO = 60;    // a partir disso: alto risco
    /** Número sem denúncias, mas com vizinhança suspeita a partir deste valor, vira "atenção". */
    public static final int VIZINHANCA_PARA_ATENCAO = 6;

    // ------------------------------------------------------------ tipos usados pelo motor

    /** O que o motor precisa saber de cada denúncia. */
    public record DadosDenuncia(Instant criadoEm, Long usuarioId, Instant contaCriadaEm, Set<TagRelato> tags) {
    }

    public record Fator(String codigo, String nome, String explicacao, int pontos, int maximo) {
    }

    public record Resultado(int score, NivelRisco nivel, String confianca, List<Fator> fatores) {
    }

    public record Definicao(String codigo, String nome, int maximo, String comoFunciona) {
    }

    // ------------------------------------------------------------ cálculo

    public Resultado calcular(List<DadosDenuncia> denuncias, TipoLinha tipoLinha, long vizinhosDenunciados,
                              boolean centralOficial, Instant agora) {
        List<Fator> fatores = List.of(
                recencia(denuncias, agora),
                pessoas(denuncias, agora),
                gravidade(denuncias),
                perfilDaLinha(denuncias, tipoLinha),
                pico(denuncias, agora),
                vizinhanca(vizinhosDenunciados));

        int score = Math.min(100, fatores.stream().mapToInt(Fator::pontos).sum());
        return new Resultado(score, nivel(score, denuncias.size(), fatores.get(5).pontos(), centralOficial),
                confianca(denuncias.size()), fatores);
    }

    Fator recencia(List<DadosDenuncia> ds, Instant agora) {
        double soma = 0;
        for (DadosDenuncia d : ds) {
            soma += Math.exp(-dias(d.criadoEm(), agora) / DIAS_DECAIMENTO);
        }
        int pontos = curva(MAX_RECENCIA, soma, SATURACAO_RECENCIA);
        String explicacao;
        if (ds.isEmpty()) {
            explicacao = "Nenhuma denúncia registrada.";
        } else {
            double maisRecente = ds.stream().mapToDouble(d -> dias(d.criadoEm(), agora)).min().orElse(0);
            explicacao = plural(ds.size(), "denúncia", "denúncias") + "; a mais recente " + quando(maisRecente)
                    + ". Denúncias antigas perdem peso com o tempo.";
        }
        return new Fator("RECENCIA", "Recência das denúncias", explicacao, pontos, MAX_RECENCIA);
    }

    Fator pessoas(List<DadosDenuncia> ds, Instant agora) {
        Map<Long, Double> pesoPorPessoa = new HashMap<>();
        int novas = 0;
        for (DadosDenuncia d : ds) {
            if (pesoPorPessoa.containsKey(d.usuarioId())) {
                continue;
            }
            double idadeConta = dias(d.contaCriadaEm(), agora);
            double peso = idadeConta < 1 ? PESO_CONTA_NOVA : idadeConta < 30 ? PESO_CONTA_RECENTE : PESO_CONTA_ANTIGA;
            if (idadeConta < 1) {
                novas++;
            }
            pesoPorPessoa.put(d.usuarioId(), peso);
        }
        double soma = pesoPorPessoa.values().stream().mapToDouble(Double::doubleValue).sum();
        int pontos = curva(MAX_PESSOAS, soma, SATURACAO_PESSOAS);
        String explicacao = pesoPorPessoa.isEmpty()
                ? "Ninguém denunciou este número."
                : "Denunciado por " + plural(pesoPorPessoa.size(), "pessoa diferente", "pessoas diferentes")
                + (novas > 0 ? " (" + novas + " com conta criada há menos de 1 dia, que pesa menos)" : "") + ".";
        return new Fator("PESSOAS", "Pessoas diferentes", explicacao, pontos, MAX_PESSOAS);
    }

    Fator gravidade(List<DadosDenuncia> ds) {
        if (ds.isEmpty()) {
            return new Fator("GRAVIDADE", "Gravidade dos relatos", "Sem relatos para analisar.", 0, MAX_GRAVIDADE);
        }
        double somaGravidade = 0;
        int criticas = 0;
        for (DadosDenuncia d : ds) {
            double g = d.tags().stream().mapToDouble(TagRelato::getGravidade).max().orElse(TagRelato.OUTRO.getGravidade());
            somaGravidade += g;
            if (g >= 1.0) {
                criticas++;
            }
        }
        double media = somaGravidade / ds.size();
        double fatorQuantidade = Math.min(1.0, ds.size() / DENUNCIAS_PARA_GRAVIDADE_CHEIA);
        int pontos = (int) Math.round(MAX_GRAVIDADE * media * fatorQuantidade);
        String explicacao = criticas > 0
                ? "Em " + plural(criticas, "relato", "relatos") + " o golpista pediu senha, código, Pix, acesso remoto "
                + "ou o cartão, o que nenhum banco faz por telefone."
                : "Os relatos não indicam pedido de senha, código ou dinheiro.";
        return new Fator("GRAVIDADE", "Gravidade dos relatos", explicacao, pontos, MAX_GRAVIDADE);
    }

    Fator perfilDaLinha(List<DadosDenuncia> ds, TipoLinha tipo) {
        int pontos = 0;
        String explicacao;
        if (ds.isEmpty()) {
            explicacao = "Sem denúncias, o tipo de linha não é avaliado.";
        } else if (tipo == TipoLinha.CELULAR) {
            pontos = MAX_LINHA;
            explicacao = "Celular se apresentando como banco: centrais bancárias usam 0800, 4004/3003 ou telefone fixo.";
        } else if (tipo == TipoLinha.FIXO) {
            pontos = 4;
            explicacao = "Telefone fixo comum, que não é uma central oficial cadastrada.";
        } else {
            explicacao = "Formato típico de central de atendimento.";
        }
        return new Fator("LINHA", "Perfil da linha", explicacao, pontos, MAX_LINHA);
    }

    Fator pico(List<DadosDenuncia> ds, Instant agora) {
        long ultimas24h = ds.stream().filter(d -> dias(d.criadoEm(), agora) <= 1.0).count();
        int pontos = (int) Math.min(MAX_PICO, Math.round(ultimas24h * (double) MAX_PICO / DENUNCIAS_PICO));
        String explicacao = ultimas24h == 0
                ? "Nenhuma denúncia nas últimas 24 horas."
                : plural(ultimas24h, "denúncia", "denúncias") + " nas últimas 24 horas"
                + (ultimas24h >= DENUNCIAS_PICO ? ": o número parece estar sendo usado agora." : ".");
        return new Fator("PICO", "Pico de denúncias", explicacao, pontos, MAX_PICO);
    }

    Fator vizinhanca(long vizinhos) {
        int pontos = (int) Math.min(MAX_VIZINHANCA, vizinhos * 2);
        String explicacao = vizinhos == 0
                ? "Nenhum número da mesma sequência foi denunciado."
                : plural(vizinhos, "número da mesma sequência foi denunciado", "números da mesma sequência foram denunciados")
                + ". Golpistas costumam usar linhas em sequência.";
        return new Fator("VIZINHANCA", "Bloco de números suspeito", explicacao, pontos, MAX_VIZINHANCA);
    }

    NivelRisco nivel(int score, int quantidadeDenuncias, int pontosVizinhanca, boolean centralOficial) {
        if (centralOficial) {
            return NivelRisco.CENTRAL_OFICIAL;
        }
        if (quantidadeDenuncias == 0) {
            return pontosVizinhanca >= VIZINHANCA_PARA_ATENCAO ? NivelRisco.ATENCAO : NivelRisco.SEM_REGISTROS;
        }
        if (score >= LIMITE_ALTO) {
            return NivelRisco.ALTO;
        }
        return score >= LIMITE_BAIXO ? NivelRisco.ATENCAO : NivelRisco.BAIXO;
    }

    String confianca(int quantidadeDenuncias) {
        if (quantidadeDenuncias >= 6) {
            return "ALTA";
        }
        return quantidadeDenuncias >= 3 ? "MEDIA" : "BAIXA";
    }

    /** Descrição do motor para a página "Como calculamos o risco". */
    public List<Definicao> definicoes() {
        List<Definicao> lista = new ArrayList<>();
        lista.add(new Definicao("RECENCIA", "Recência das denúncias", MAX_RECENCIA,
                "Cada denúncia vale e^(-dias/15): uma denúncia de hoje vale 1; de 15 dias atrás, 0,37; de 60 dias, 0,02. "
                        + "A soma passa pela curva 25 × (1 − e^(−soma/2))."));
        lista.add(new Definicao("PESSOAS", "Pessoas diferentes", MAX_PESSOAS,
                "Conta cada pessoa uma vez só. Conta criada há menos de 1 dia vale 0,3; até 30 dias, 0,7; mais antiga, 1. "
                        + "A soma passa pela curva 25 × (1 − e^(−soma/2,5)). Isso dificulta criar várias contas para acusar um número inocente."));
        lista.add(new Definicao("GRAVIDADE", "Gravidade dos relatos", MAX_GRAVIDADE,
                "A IA lê o comentário de cada denúncia e marca os tópicos (pediu senha, Pix, acesso remoto...). "
                        + "Cada tópico tem uma gravidade de 0 a 1. Pontos = 20 × média da gravidade × min(1, denúncias/3)."));
        lista.add(new Definicao("LINHA", "Perfil da linha", MAX_LINHA,
                "Celular denunciado se apresentando como banco: 10 pontos. Telefone fixo: 4. 0800 e 4004/3003: 0."));
        lista.add(new Definicao("PICO", "Pico de denúncias", MAX_PICO,
                "Denúncias nas últimas 24 horas × 3,3, até 10. Três ou mais em um dia indicam que o número está em uso agora."));
        lista.add(new Definicao("VIZINHANCA", "Bloco de números suspeito", MAX_VIZINHANCA,
                "Conta outros números denunciados com o mesmo prefixo (DDD + 5 primeiros dígitos no celular). "
                        + "2 pontos por vizinho, até 10. Um número novo num bloco muito denunciado já aparece como \"atenção\"."));
        return lista;
    }

    // ------------------------------------------------------------ auxiliares

    private static int curva(int maximo, double x, double saturacao) {
        return (int) Math.round(maximo * (1 - Math.exp(-x / saturacao)));
    }

    private static double dias(Instant inicio, Instant fim) {
        if (inicio == null) {
            return 365;
        }
        return Math.max(0, Duration.between(inicio, fim).toMinutes() / 1440.0);
    }

    private static String quando(double dias) {
        if (dias < 1) {
            long horas = Math.max(1, Math.round(dias * 24));
            return "há " + plural(horas, "hora", "horas");
        }
        return "há " + plural(Math.round(dias), "dia", "dias");
    }

    private static String plural(long n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
