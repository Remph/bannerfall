package de.bannerfall.banner;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerEventRepository extends JpaRepository<BannerEvent, Long> {

    /** The log of a banner, oldest first. */
    List<BannerEvent> findByBannerIdOrderByIdAsc(long bannerId);

    /** Lines of one kind that went to a player after {@code after}: the drops of the last day. */
    long countByKindAndToPlayerIdAndHappenedAtAfter(
            BannerEventKind kind, long toPlayerId, Instant after);
}
