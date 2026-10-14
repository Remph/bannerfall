package de.bannerfall.abuse;

import java.time.Instant;

/** One recorded suspicious request (M14e). */
public record AbuseEventDto(long id, AbuseKind kind, String endpoint, String detail, Instant at) {}
