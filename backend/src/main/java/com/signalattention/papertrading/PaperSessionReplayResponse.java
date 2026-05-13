package com.signalattention.papertrading;

import java.time.Instant;

public record PaperSessionReplayResponse(
        Long paperSessionId,
        int candlesRead,
        int signalsProcessed,
        int filledOrders,
        int rejectedOrders,
        Instant startDate,
        Instant endDate
) {
}
