package com.signalattention.strategies;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signalattention.audit.AuditService;
import com.signalattention.common.ResourceNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategyService {

    private static final String ENTITY_TYPE = "STRATEGY";

    private final StrategyRepository strategyRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public StrategyService(StrategyRepository strategyRepository, AuditService auditService, ObjectMapper objectMapper) {
        this.strategyRepository = strategyRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StrategyResponse create(StrategyCreateRequest request) {
        Strategy strategy = new Strategy(
                request.name(),
                request.symbol(),
                request.timeframe(),
                request.strategyType(),
                toRulesJson(request.rules()),
                StrategyStatus.ACTIVE
        );

        Strategy saved = strategyRepository.save(strategy);
        auditService.record(ENTITY_TYPE, stringId(saved), "STRATEGY_CREATED", "Strategy created", metadataJson(saved));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StrategyResponse> list() {
        return strategyRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StrategyResponse get(Long id) {
        return toResponse(findStrategy(id));
    }

    @Transactional
    public StrategyResponse update(Long id, StrategyUpdateRequest request) {
        Strategy strategy = findStrategy(id);
        strategy.setName(request.name());
        strategy.setSymbol(request.symbol());
        strategy.setTimeframe(request.timeframe());
        strategy.setStrategyType(request.strategyType());
        strategy.setRulesJson(toRulesJson(request.rules()));
        strategy.setStatus(request.status());

        Strategy saved = strategyRepository.save(strategy);
        auditService.record(ENTITY_TYPE, stringId(saved), "STRATEGY_UPDATED", "Strategy updated", metadataJson(saved));
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Strategy strategy = findStrategy(id);
        strategyRepository.delete(strategy);
        auditService.record(ENTITY_TYPE, stringId(strategy), "STRATEGY_DELETED", "Strategy deleted", metadataJson(strategy));
    }

    private Strategy findStrategy(Long id) {
        return strategyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Strategy not found: " + id));
    }

    private StrategyResponse toResponse(Strategy strategy) {
        return new StrategyResponse(
                strategy.getId(),
                strategy.getName(),
                strategy.getSymbol(),
                strategy.getTimeframe(),
                strategy.getStrategyType(),
                fromRulesJson(strategy.getRulesJson()),
                strategy.getStatus(),
                strategy.getCreatedAt(),
                strategy.getUpdatedAt()
        );
    }

    private String toRulesJson(SmaCrossoverRulesRequest rules) {
        try {
            return objectMapper.writeValueAsString(rules);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize strategy rules", exception);
        }
    }

    private SmaCrossoverRulesRequest fromRulesJson(String rulesJson) {
        try {
            return objectMapper.readValue(rulesJson, SmaCrossoverRulesRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize strategy rules", exception);
        }
    }

    private String metadataJson(Strategy strategy) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("strategyId", strategy.getId());
            metadata.put("symbol", strategy.getSymbol());
            metadata.put("timeframe", strategy.getTimeframe());
            metadata.put("strategyType", strategy.getStrategyType());
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit metadata", exception);
        }
    }

    private String stringId(Strategy strategy) {
        if (strategy.getId() == null) {
            return null;
        }
        return strategy.getId().toString();
    }
}
