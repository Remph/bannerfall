package de.bannerfall.admin;

import java.time.Instant;

/**
 * {@code POST /api/admin/setup-code} (1.6, guided world setup): the one-time code that makes the
 * logged-in account that sends it an admin ({@code POST /api/me/setup-claim}).
 *
 * @param code ten letters and digits, shown once
 * @param expiresAt when it stops working (30 minutes)
 */
public record SetupCodeDto(String code, Instant expiresAt) {}
