package de.bannerfall.market;

import de.bannerfall.gamedata.ResourceAmounts;
import java.time.Instant;

/**
 * Trading with the market itself (02-game-rules §12.1), for one village.
 *
 * @param ratio what 1 of a resource costs in another (0 = off in this world)
 * @param dailyCap how much of each resource the village may get per day (0 without market)
 * @param received what it got of each resource today
 * @param resetsAt when the day's amounts start again (midnight)
 */
public record ExchangeDto(double ratio, int dailyCap, ResourceAmounts received, Instant resetsAt) {}
