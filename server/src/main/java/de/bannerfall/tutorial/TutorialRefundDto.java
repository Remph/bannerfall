package de.bannerfall.tutorial;

import de.bannerfall.gamedata.ResourceAmounts;

/** A refund paid by this very call (the web client shows it as a toast), once. */
public record TutorialRefundDto(TutorialStep key, long villageId, ResourceAmounts amount) {}
