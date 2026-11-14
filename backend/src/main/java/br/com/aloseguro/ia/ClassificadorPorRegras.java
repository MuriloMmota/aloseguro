package br.com.aloseguro.ia;

import br.com.aloseguro.model.TagRelato;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plano B da classificação: procura palavras-chave no relato.
 * É usado quando a IA está desligada, sem internet ou demora para responder,
 * e também serve de base de comparação para os testes.
 */
@Component
public class ClassificadorPorRegras {

    private static final Map<TagRelato, List<String>> PALAVRAS = new LinkedHashMap<>();

    static {
        PALAVRAS.put(TagRelato.PEDIU_SENHA, List.of("senha", "token", "assinatura eletronica"));
        PALAVRAS.put(TagRelato.PEDIU_CODIGO, List.of("codigo", "sms", "digitos que chegaram", "verificacao"));
        PALAVRAS.put(TagRelato.PEDIU_PIX, List.of("pix", "transfer", "deposito", "conta segura", "boleto", "estorno"));
        PALAVRAS.put(TagRelato.ACESSO_REMOTO, List.of("instalar", "aplicativo", "anydesk", "teamviewer",
                "acesso remoto", "baixar o app", "link para baixar"));
        PALAVRAS.put(TagRelato.RETIRADA_CARTAO, List.of("motoboy", "buscar o cartao", "retirar o cartao",
                "entregar o cartao", "cortar o cartao", "recolher o cartao"));
        PALAVRAS.put(TagRelato.DADOS_PESSOAIS, List.of("cpf", "data de nascimento", "nome da mae", "endereco",
                "confirmar meus dados", "confirmei meus dados"));
        PALAVRAS.put(TagRelato.FALSO_SETOR, List.of("setor", "seguranca", "ouvidoria", "musica de espera",
                "transferiu a ligacao", "gerente"));
        PALAVRAS.put(TagRelato.URGENCIA, List.of("urgente", "imediatamente", "bloquead", "prazo", "ameac",
                "policia", "investigacao", "agora mesmo", "nervos"));
        PALAVRAS.put(TagRelato.COMPRA_SUSPEITA, List.of("compra", "invadid", "clonad", "fraude",
                "movimentacao suspeita", "transacao suspeita"));
    }

    public Set<TagRelato> classificar(String texto) {
        Set<TagRelato> tags = EnumSet.noneOf(TagRelato.class);
        if (texto == null || texto.isBlank()) {
            return tags;
        }
        String normalizado = normalizar(texto);
        PALAVRAS.forEach((tag, palavras) -> {
            if (palavras.stream().anyMatch(normalizado::contains)) {
                tags.add(tag);
            }
        });
        return tags;
    }

    /** Minúsculas e sem acentos, para "Código" e "codigo" casarem. */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
