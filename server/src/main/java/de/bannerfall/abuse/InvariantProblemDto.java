package de.bannerfall.abuse;

import java.util.List;

/**
 * One kind of impossible state (M14e), e.g. {@code NEGATIVE_RESOURCES}.
 *
 * @param count how many rows have it
 * @param examples up to ten of them, e.g. {@code "village 12"}
 */
public record InvariantProblemDto(String kind, long count, List<String> examples) {}
