package de.bannerfall.banner;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    /** Where a banner is, without loading it into the persistence context. */
    record Place(long ownerId, Long villageId) {}

    @Query(
            "select new de.bannerfall.banner.BannerRepository$Place(b.ownerId, b.equippedVillageId)"
                    + " from Banner b where b.id = :id")
    Optional<Place> findPlace(long id);

    /** All banners of a player, stash and equipped, oldest first. */
    List<Banner> findByOwnerIdOrderByIdAsc(long ownerId);

    /**
     * What is equipped in a village: its banner and its paladin standard (1.4), at most one each.
     */
    List<Banner> findByEquippedVillageIdOrderByIdAsc(long villageId);

    /** The banner flying in a village, if any (not its paladin standard). */
    default Optional<Banner> findFlying(long villageId) {
        return findByEquippedVillageIdOrderByIdAsc(villageId).stream()
                .filter(banner -> !banner.isStandard())
                .findFirst();
    }

    /** The paladin standard equipped in a village, if any (1.4, 02-game-rules §21.18). */
    default Optional<Banner> findStandard(long villageId) {
        return findByEquippedVillageIdOrderByIdAsc(villageId).stream()
                .filter(Banner::isStandard)
                .findFirst();
    }

    /** The banners and standards equipped in these villages. */
    List<Banner> findByEquippedVillageIdIn(Collection<Long> villageIds);

    Optional<Banner> findByIdAndOwnerId(long id, long ownerId);
}
