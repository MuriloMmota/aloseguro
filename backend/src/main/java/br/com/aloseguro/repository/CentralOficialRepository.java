package br.com.aloseguro.repository;

import br.com.aloseguro.model.CentralOficial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CentralOficialRepository extends JpaRepository<CentralOficial, Long> {

    Optional<CentralOficial> findByNumero(String numero);
}
