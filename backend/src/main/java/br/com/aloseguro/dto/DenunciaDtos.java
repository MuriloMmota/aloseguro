package br.com.aloseguro.dto;

import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.util.NumeroTelefone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Objetos de entrada e saída das denúncias.
 */
public final class DenunciaDtos {

    private DenunciaDtos() {
    }

    public record DenunciaRequest(
            @NotBlank(message = "é obrigatório") String numero,
            @Size(max = 100, message = "deve ter no máximo 100 caracteres") String bancoAlegado,
            @NotNull(message = "é obrigatório") TipoAbordagem tipoAbordagem,
            @Size(max = 500, message = "deve ter no máximo 500 caracteres") String descricao,
            @PastOrPresent(message = "não pode estar no futuro") LocalDateTime dataHoraLigacao) {
    }

    /** Edição de uma denúncia: o número denunciado não pode ser trocado. */
    public record DenunciaUpdateRequest(
            @Size(max = 100, message = "deve ter no máximo 100 caracteres") String bancoAlegado,
            @NotNull(message = "é obrigatório") TipoAbordagem tipoAbordagem,
            @Size(max = 500, message = "deve ter no máximo 500 caracteres") String descricao,
            @PastOrPresent(message = "não pode estar no futuro") LocalDateTime dataHoraLigacao) {
    }

    public record TagDto(String tag, String descricao) {
    }

    public record DenunciaResponse(
            Long id,
            String numero,
            String numeroFormatado,
            String bancoAlegado,
            TipoAbordagem tipoAbordagem,
            String tipoAbordagemDescricao,
            String descricao,
            LocalDateTime dataHoraLigacao,
            List<TagDto> tags,
            String tagsOrigem,
            Instant registradaEm) {

        public static DenunciaResponse de(Denuncia d) {
            String numero = d.getNumero().getNumero();
            List<TagDto> tags = d.getTags().stream()
                    .map(t -> new TagDto(t.name(), t.getDescricao()))
                    .toList();
            return new DenunciaResponse(
                    d.getId(),
                    numero,
                    NumeroTelefone.formatar(numero),
                    d.getBancoAlegado(),
                    d.getTipoAbordagem(),
                    d.getTipoAbordagem().getDescricao(),
                    d.getDescricao(),
                    d.getDataHoraLigacao(),
                    tags,
                    d.getTagsOrigem(),
                    d.getCriadoEm());
        }
    }

    /** Usado só para documentar a lista de tags possíveis. */
    public static List<TagDto> todasAsTags() {
        return java.util.Arrays.stream(TagRelato.values()).map(t -> new TagDto(t.name(), t.getDescricao())).toList();
    }
}
