package de.bannerfall.common;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/** The real dice: JDK default generator (thread-safe enough for a game of friends). */
@Component
public class DefaultRandomSource implements RandomSource {

    private final RandomGenerator random = RandomGenerator.getDefault();

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }

    @Override
    public int nextInt(int minIncl, int maxIncl) {
        return random.nextInt(minIncl, maxIncl + 1);
    }
}
