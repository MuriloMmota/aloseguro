package br.com.aloseguro.dto;

import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.TipoLinha;

import java.time.Instant;
import java.util.List;

/**
 * Objetos de saída da consulta de números, do ranking e da página do motor de risco.
 */
public final class ConsultaDtos {

    private ConsultaDtos() {
    }

    public record ConsultaResponse(
            String numero,
            String numeroFormatado,
            TipoLinha tipoLinha,
            String tipoLinhaDescricao,
            NivelRisco nivel,
            int score,
            String mensagem,
            String confianca,
            List<FatorResponse> fatores,
            List<TopicoResponse> topicos,
            List<RelatoResponse> relatosRecentes,
            long totalDenuncias,
            long denunciasUltimos30Dias,
            long pessoasQueDenunciaram,
            Instant ultimaDenuncia,
            String bancoOficial) {
    }

    /** Um fator do motor de risco, com a explicação e quantos pontos somou. */
    public record FatorResponse(String codigo, String nome, String explicacao, int pontos, int maximo) {
    }

    /** Um tópico relatado (tag) e em quantas denúncias ele apareceu. */
    public record TopicoResponse(String tag, String descricao, double gravidade, long quantidade) {
    }

    /** Trecho de uma denúncia, sem dados de quem denunciou. */
    public record RelatoResponse(String descricao, List<String> tags, Instant registradoEm) {
    }

    public record RankingItemResponse(
            String numero,
            String numeroFormatado,
            long totalDenuncias,
            Instant ultimaDenuncia) {
    }

    // ------------------------------------------------------------ explicação do motor

    public record MotorResponse(
            String versao,
            List<DefinicaoFatorResponse> fatores,
            List<FaixaResponse> faixas,
            List<TagResponse> tags,
            String classificacao) {
    }

    public record DefinicaoFatorResponse(String codigo, String nome, int maximo, String comoFunciona) {
    }

    public record FaixaResponse(String nivel, String faixa, String significado) {
    }

    public record TagResponse(String tag, String descricao, double gravidade) {
    }

    public record StatusResponse(
            String aplicacao,
            String versao,
            boolean iaAtiva,
            String iaProvedor,
            String iaModelo,
            Instant iaUltimoSucesso,
            String iaUltimoErro,
            long numerosCadastrados,
            long denunciasRegistradas,
            Instant agora) {
    }
}
