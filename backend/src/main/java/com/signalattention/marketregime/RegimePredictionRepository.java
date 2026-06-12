package com.signalattention.marketregime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegimePredictionRepository extends JpaRepository<RegimePrediction, Long> {

    List<RegimePrediction> findByRegimeRunIdOrderByWindowStartAsc(Long regimeRunId);
}
