package br.com.aloseguro.repository;

import br.com.aloseguro.model.NumeroSuspeito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NumeroSuspeitoRepository extends JpaRepository<NumeroSuspeito, Long> {

    Optional<NumeroSuspeito> findByNumero(String numero);
}
