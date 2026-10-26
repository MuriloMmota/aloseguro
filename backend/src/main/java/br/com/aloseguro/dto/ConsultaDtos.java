package br.com.aloseguro.dto;

import br.com.aloseguro.model.NivelRisco;
import br.com.aloseguro.model.TipoLinha;

import java.time.Instant;
import java.util.List;

/**
 * Objetos de saída da consulta de números e do ranking.
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
            List<String> motivos,
            long totalDenuncias,
            long denunciasUltimos30Dias,
            long pessoasQueDenunciaram,
            String bancoOficial) {
    }

    public record RankingItemResponse(
            String numero,
            String numeroFormatado,
            long totalDenuncias,
            Instant ultimaDenuncia) {
    }
}
