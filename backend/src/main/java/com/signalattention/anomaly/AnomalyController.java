package com.signalattention.anomaly;

import com.signalattention.ml.MlAnomalyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @PostMapping("/api/anomaly-check")
    public MlAnomalyResponse checkAnomaly(@Valid @RequestBody AnomalyCheckRequest request) {
        return anomalyService.check(request);
    }
}
