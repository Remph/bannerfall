package de.bannerfall.account;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/auth/options (1.5): how players log in here, for the login, registration and password
 * pages.
 *
 * @param accountServerName what the account server's button says it logs in with (e.g. {@code
 *     example.com}); null when the mode is {@code OFF}
 * @param careerReports the world tells the account server how its linked players are doing, for
 *     their career page there (01-architecture §4.9b); for the privacy page
 */
public record AuthOptionsDto(
        AccountLoginMode mode,
        @Schema(nullable = true) String accountServerName,
        boolean careerReports) {}
