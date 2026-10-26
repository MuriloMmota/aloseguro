package br.com.aloseguro.repository;

import br.com.aloseguro.model.Denuncia;
import br.com.aloseguro.model.NumeroSuspeito;
import br.com.aloseguro.model.TipoAbordagem;
import br.com.aloseguro.model.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    long countByNumero(NumeroSuspeito numero);

    long countByNumeroAndCriadoEmAfter(NumeroSuspeito numero, Instant desde);

    long countByNumeroAndTipoAbordagemIn(NumeroSuspeito numero, Collection<TipoAbordagem> tipos);

    boolean existsByUsuarioAndNumeroAndCriadoEmAfter(Usuario usuario, NumeroSuspeito numero, Instant desde);

    @Query("select count(distinct d.usuario.id) from Denuncia d where d.numero = :numero")
    long contarUsuariosDistintos(@Param("numero") NumeroSuspeito numero);

    @Query("select d from Denuncia d join fetch d.numero where d.usuario = :usuario order by d.criadoEm desc")
    List<Denuncia> buscarDoUsuario(@Param("usuario") Usuario usuario);

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
