package br.com.aloseguro.repository;

import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    boolean existsByUsuarioAndNumeroAndCriadoEmAfter(Usuario usuario, NumeroSuspeito numero, Instant desde);

    /** Todas as denúncias de um número, já com o autor carregado (o motor usa a idade da conta). */
    @Query("select d from Denuncia d join fetch d.usuario where d.numero = :numero order by d.criadoEm desc")
    List<Denuncia> buscarDoNumero(@Param("numero") NumeroSuspeito numero);

    @Query("select d from Denuncia d join fetch d.numero where d.usuario = :usuario order by d.criadoEm desc")
    List<Denuncia> buscarDoUsuario(@Param("usuario") Usuario usuario);

    /**
     * Quantos OUTROS números do mesmo bloco de numeração (mesmo prefixo) têm denúncia.
     * Golpistas costumam usar linhas em sequência.
     */
    @Query("""
            select count(distinct d.numero.id) from Denuncia d
            where d.numero.numero like :prefixo and d.numero.numero <> :numero
            """)
    long contarVizinhosDenunciados(@Param("prefixo") String prefixo, @Param("numero") String numero);

    @Modifying
    @Query("delete from Denuncia d where d.usuario = :usuario")
    int apagarDoUsuario(@Param("usuario") Usuario usuario);

    @Query("""
            select d.numero.numero as numero, count(d) as total, max(d.criadoEm) as ultima
            from Denuncia d
            where d.criadoEm >= :desde
            group by d.numero.numero
            order by count(d) desc, max(d.criadoEm) desc
            """)
    List<RankingNumero> ranking(@Param("desde") Instant desde, Pageable pagina);

    /** Projeção usada pela listagem dos números mais denunciados. */
    interface RankingNumero {
        String getNumero();

        Long getTotal();

        Instant getUltima();
    }
}
