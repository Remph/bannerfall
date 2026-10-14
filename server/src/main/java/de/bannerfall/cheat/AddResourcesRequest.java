package de.bannerfall.cheat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** POST /api/dev/villages/{id}/resources: how much of each resource to add. */
public record AddResourcesRequest(
        @Min(0) @Max(AddResourcesRequest.MAX) int wood,
        @Min(0) @Max(AddResourcesRequest.MAX) int clay,
        @Min(0) @Max(AddResourcesRequest.MAX) int iron) {

    /** Far more than any warehouse holds; keeps the sums from overflowing. */
    static final int MAX = 100_000_000;
}
