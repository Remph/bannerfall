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

    /** The banner flying in a village, if any. */
    Optional<Banner> findByEquippedVillageId(long villageId);

    /** The banners flying in these villages. */
    List<Banner> findByEquippedVillageIdIn(Collection<Long> villageIds);

    Optional<Banner> findByIdAndOwnerId(long id, long ownerId);
}
