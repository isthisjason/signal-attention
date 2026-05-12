package com.signalattention.risk;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskPolicyRepository extends JpaRepository<RiskPolicy, Long> {

    Optional<RiskPolicy> findByStrategyId(Long strategyId);
}
