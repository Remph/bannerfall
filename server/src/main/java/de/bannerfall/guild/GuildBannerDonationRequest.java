package de.bannerfall.guild;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** {@code POST /api/guilds/mine/banner/donations}: what one village gives to the guild fund. */
public record GuildBannerDonationRequest(
        @Min(1) long villageId,
        @Min(0) @Max(10_000_000) int wood,
        @Min(0) @Max(10_000_000) int clay,
        @Min(0) @Max(10_000_000) int iron) {}
