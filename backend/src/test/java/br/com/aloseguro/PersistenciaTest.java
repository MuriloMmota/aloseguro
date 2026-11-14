package br.com.aloseguro;

import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.TagRelato;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.model.TipoLinha;
import br.com.aloseguro.model.Usuario;
import br.com.aloseguro.repository.DenunciaRepository;
import br.com.aloseguro.repository.NumeroSuspeitoRepository;
import br.com.aloseguro.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de persistência: CRUD direto nos repositórios e regras de integridade do banco.
 * Cada teste roda numa transação desfeita no final (@Transactional), sem sujar os outros testes.
 */
@SpringBootTest
@Transactional
class PersistenciaTest {
    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private NumeroSuspeitoRepository numeros;

    @Autowired
    private DenunciaRepository denuncias;

    @Test
    @DisplayName("CRUD de denúncia no banco: criar, ler, atualizar e excluir")
    void crudDenuncia() {
        Usuario u = usuarios.save(new Usuario("Persistência", "persistencia1@teste.com", "hash"));
        NumeroSuspeito n = numeros.save(new NumeroSuspeito("21911112222", "21", TipoLinha.CELULAR));

        // CREATE
        Denuncia d = new Denuncia(n, u, "Banco X", TipoAbordagem.OUTRO, "texto", null);
        d.definirTags(EnumSet.of(TagRelato.OUTRO), "REGRAS");
        Long id = denuncias.saveAndFlush(d).getId();
        assertNotNull(id);
        assertNotNull(denuncias.findById(id).orElseThrow().getCriadoEm(), "data de criação preenchida automaticamente");

        // READ
        assertEquals(1, denuncias.buscarDoNumero(n).size());
        assertEquals(1, denuncias.buscarDoUsuario(u).size());

        // UPDATE
        Denuncia salva = denuncias.findById(id).orElseThrow();
        salva.atualizar("Banco Y", TipoAbordagem.PEDIU_SENHA, "pediu a senha", null);
        salva.definirTags(EnumSet.of(TagRelato.PEDIU_SENHA), "REGRAS");
        denuncias.saveAndFlush(salva);
        Denuncia relida = denuncias.findById(id).orElseThrow();
        assertEquals("Banco Y", relida.getBancoAlegado());
        assertTrue(relida.getTags().contains(TagRelato.PEDIU_SENHA));

        // DELETE
        denuncias.deleteById(id);
        denuncias.flush();
        assertFalse(denuncias.existsById(id));
    }

    @Test
    @DisplayName("Integridade: e-mail de usuário é único")
    void emailUnico() {
        usuarios.saveAndFlush(new Usuario("A", "repetido@teste.com", "hash"));
        assertThrows(DataIntegrityViolationException.class,
                () -> usuarios.saveAndFlush(new Usuario("B", "repetido@teste.com", "hash")));
    }

    @Test
    @DisplayName("Integridade: o mesmo número não é cadastrado duas vezes")
    void numeroUnico() {
        numeros.saveAndFlush(new NumeroSuspeito("31933334444", "31", TipoLinha.CELULAR));
        assertThrows(DataIntegrityViolationException.class,
                () -> numeros.saveAndFlush(new NumeroSuspeito("31933334444", "31", TipoLinha.CELULAR)));
    }

    @Test
    @DisplayName("Integridade: não dá para apagar um usuário que tem denúncias (chave estrangeira)")
    void chaveEstrangeira() {
        Usuario u = usuarios.save(new Usuario("FK", "fk@teste.com", "hash"));
        NumeroSuspeito n = numeros.save(new NumeroSuspeito("41955556666", "41", TipoLinha.CELULAR));
        Denuncia d = new Denuncia(n, u, null, TipoAbordagem.OUTRO, null, null);
        d.definirTags(EnumSet.of(TagRelato.OUTRO), "REGRAS");
        denuncias.saveAndFlush(d);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM usuarios WHERE id = ?", u.getId()));
    }

    @Test
    @DisplayName("Consistência: tags são gravadas e lidas sem perda")
    void tagsIdaEVolta() {
        Usuario u = usuarios.save(new Usuario("Tags", "tags@teste.com", "hash"));
        NumeroSuspeito n = numeros.save(new NumeroSuspeito("51977778888", "51", TipoLinha.CELULAR));
        Denuncia d = new Denuncia(n, u, null, TipoAbordagem.OUTRO, null, null);
        d.definirTags(EnumSet.of(TagRelato.PEDIU_PIX, TagRelato.URGENCIA, TagRelato.FALSO_SETOR), "IA");
        Long id = denuncias.saveAndFlush(d).getId();

        Denuncia lida = denuncias.findById(id).orElseThrow();
        assertEquals(EnumSet.of(TagRelato.PEDIU_PIX, TagRelato.URGENCIA, TagRelato.FALSO_SETOR), lida.getTags());
        assertEquals("IA", lida.getTagsOrigem());
    }
}
