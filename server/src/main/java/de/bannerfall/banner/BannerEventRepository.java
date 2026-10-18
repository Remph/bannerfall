package de.bannerfall.banner;

import de.bannerfall.gamedata.BannerType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerEventRepository extends JpaRepository<BannerEvent, Long> {

    /** The log of a banner, oldest first. */
    List<BannerEvent> findByBannerIdOrderByIdAsc(long bannerId);

    /**
     * Lines of one kind about banners of these types that went to a player after {@code after}: the
     * drops of the last day, counted per family (village banners and paladin standards have a cap
     * each, 1.4).
     */
    long countByKindAndToPlayerIdAndHappenedAtAfterAndTypeIn(
            BannerEventKind kind, long toPlayerId, Instant after, Collection<BannerType> types);
}
