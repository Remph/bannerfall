package de.bannerfall.banner;

import static org.assertj.core.api.Assertions.assertThat;

import de.bannerfall.IntegrationTest;
import de.bannerfall.TestPlayer;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.player.Player;
import de.bannerfall.player.PlayerRepository;
import de.bannerfall.rules.BannerEffect;
import de.bannerfall.village.Village;
import de.bannerfall.village.VillageRepository;
import de.bannerfall.world.WorldService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Who a banner works for (02-game-rules §21.6.1, §21.8): human players with a Banner Hall. A
 * village an NPC lord takes keeps the banner, but dormant; barbarians carry none. Seen from the
 * service, without the HTTP layer.
 */
@IntegrationTest
class VillageBannersTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired VillageBanners villageBanners;
    @Autowired BannerService bannerService;
    @Autowired VillageRepository villages;
    @Autowired PlayerRepository players;
    @Autowired TransactionTemplate tx;
    @Autowired WorldService worldService;

    private TestPlayer owner;
    private long banner;
    private Long lordId;

    @BeforeEach
    void setUp() throws Exception {
        owner = TestPlayer.register(mockMvc);
        jdbc.sql("update village set b_banner_hall = 3 where id = ?")
                .param(owner.villageId)
                .update();
        banner =
                jdbc.sql(
                                """
                                insert into banner (owner_id, type, tier, equipped_village_id,
                                                    equipped_at, origin, created_at)
                                values (?, 'HARVEST', 3, ?, now(), 'GRANTED', now()) returning id
                                """)
                        .params(owner.playerId, owner.villageId)
                        .query(Long.class)
                        .single();
    }

    @AfterEach
    void humanAgain() {
        if (lordId != null) {
            jdbc.sql("update player set npc = false where id = ?").param(lordId).update();
        }
        setBanners(true);
    }

    @Test
    void theOwnersBannerWorksAtTheTierTheHallAllows() {
        BannerEffect effect = effectOf(owner.villageId);

        assertThat(effect.type()).isEqualTo(BannerType.HARVEST);
        assertThat(effect.effectiveTier()).isEqualTo(3);
        assertThat(effect.bonus()).isEqualTo(0.15);
        assertThat(effect.harvest()).isEqualTo(0.15);
        assertThat(effect.builders()).isZero();
    }

    @Test
    void aBannerWithoutAHallSleeps() {
        jdbc.sql("update village set b_banner_hall = 0 where id = ?")
                .param(owner.villageId)
                .update();

        assertThat(effectOf(owner.villageId).effectiveTier()).isZero();
        assertThat(effectOf(owner.villageId).harvest()).isZero();
    }

    @Test
    void aVillageAnNpcLordTakesKeepsTheBannerButItIsDormant() {
        TestPlayer lord = registerPlayer();
        // a made-up lord: back to a human afterwards, other tests count the NPC players
        lordId = lord.playerId;
        jdbc.sql("update player set npc = true where id = ?").param(lord.playerId).update();

        tx.executeWithoutResult(
                status -> {
                    Village village = villages.findById(owner.villageId).orElseThrow();
                    Player npc = players.findById(lord.playerId).orElseThrow();
                    village.conquer(npc, "Lord", Instant.now());
                    bannerService.villageConquered(village, npc, Instant.now());
                });

        // still in the village, now the lord's: it neither works nor reaches a stash
        assertThat(bannerOwner()).isEqualTo(lord.playerId);
        assertThat(flyingIn()).isEqualTo(owner.villageId);
        assertThat(effectOf(owner.villageId)).isEqualTo(BannerEffect.NONE);
    }

    @Test
    void aHumanConquerorGetsTheBannerIntoTheStash() {
        TestPlayer winner = registerPlayer();

        tx.executeWithoutResult(
                status -> {
                    Village village = villages.findById(owner.villageId).orElseThrow();
                    Player human = players.findById(winner.playerId).orElseThrow();
                    village.conquer(human, "Winner", Instant.now());
                    bannerService.villageConquered(village, human, Instant.now());
                });

        assertThat(bannerOwner()).isEqualTo(winner.playerId);
        assertThat(flyingIn()).isNull();
    }

    @Test
    void withBannersOffAConquestLeavesTheBannerInTheVillage() {
        TestPlayer winner = registerPlayer();
        setBanners(false);

        tx.executeWithoutResult(
                status -> {
                    Village village = villages.findById(owner.villageId).orElseThrow();
                    Player human = players.findById(winner.playerId).orElseThrow();
                    village.conquer(human, "Winner", Instant.now());
                    bannerService.villageConquered(village, human, Instant.now());
                    // it sleeps, and the village page says so
                    assertThat(villageBanners.flying(village).effectiveTier()).isZero();
                });

        // §21.9: no theft while banners are off, the banner goes with the village
        assertThat(bannerOwner()).isEqualTo(winner.playerId);
        assertThat(flyingIn()).isEqualTo(owner.villageId);
        assertThat(effectOf(owner.villageId)).isEqualTo(BannerEffect.NONE);

        setBanners(true);
        assertThat(effectOf(owner.villageId).harvest()).isEqualTo(0.15);
    }

    @Test
    void aVillageWithoutABannerOrAnOwnerHasNoEffect() {
        jdbc.sql("delete from banner where id = ?").param(banner).update();
        assertThat(effectOf(owner.villageId)).isEqualTo(BannerEffect.NONE);

        tx.executeWithoutResult(
                status -> {
                    Village village = villages.findById(owner.villageId).orElseThrow();
                    village.becomeBarbarian();
                    assertThat(villageBanners.effectOf(village)).isEqualTo(BannerEffect.NONE);
                    assertThat(villageBanners.flying(village)).isNull();
                });
    }

    @Test
    void destroyingAPlayersBannersLeavesALogLinePerBanner() {
        tx.executeWithoutResult(status -> bannerService.destroyAllOf(owner.playerId, "banned"));

        assertThat(
                        jdbc.sql("select count(*) from banner where owner_id = ?")
                                .param(owner.playerId)
                                .query(Long.class)
                                .single())
                .isZero();
        assertThat(
                        jdbc.sql(
                                        "select detail from banner_event where banner_id = ? and"
                                                + " kind = 'DESTROYED'")
                                .param(banner)
                                .query(String.class)
                                .single())
                .isEqualTo("banned");
    }

    private void setBanners(boolean enabled) {
        jdbc.sql("update world_settings set banners_enabled = ?").param(enabled).update();
        worldService.reload();
    }

    private TestPlayer registerPlayer() {
        try {
            return TestPlayer.register(mockMvc);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private BannerEffect effectOf(long villageId) {
        return tx.execute(
                status -> villageBanners.effectOf(villages.findById(villageId).orElseThrow()));
    }

    private long bannerOwner() {
        return jdbc.sql("select owner_id from banner where id = ?")
                .param(banner)
                .query(Long.class)
                .single();
    }

    private Long flyingIn() {
        return jdbc.sql("select equipped_village_id from banner where id = ?")
                .param(banner)
                .query((rs, n) -> rs.getObject(1, Long.class))
                .list()
                .getFirst();
    }
}
