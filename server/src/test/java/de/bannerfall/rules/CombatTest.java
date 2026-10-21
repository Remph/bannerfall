package de.bannerfall.rules;

import static de.bannerfall.gamedata.UnitType.AXEMAN;
import static de.bannerfall.gamedata.UnitType.CATAPULT;
import static de.bannerfall.gamedata.UnitType.LIGHT_CAVALRY;
import static de.bannerfall.gamedata.UnitType.NOBLE;
import static de.bannerfall.gamedata.UnitType.RAM;
import static de.bannerfall.gamedata.UnitType.SCOUT;
import static de.bannerfall.gamedata.UnitType.SPEARMAN;
import static de.bannerfall.gamedata.UnitType.SWORDSMAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import de.bannerfall.FixedRandomSource;
import de.bannerfall.TestGameData;
import de.bannerfall.gamedata.BannerType;
import de.bannerfall.gamedata.BuildingType;
import de.bannerfall.gamedata.GameData;
import de.bannerfall.gamedata.UnitType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CombatTest {

    /** The rulebook gives the ratios with five decimals. */
    private static final Offset<Double> FIVE_DECIMALS = within(0.000005);

    private final GameData data = TestGameData.load();

    /** War and Bulwark (M32b, 02-game-rules §21.2): multipliers on A and D. */
    @Nested
    class BannersInBattle {

        private final Map<UnitType, Integer> axemen = Map.of(AXEMAN, 100);
        private final List<Map<UnitType, Integer>> spearmen = List.of(Map.of(SPEARMAN, 100));

        @Test
        void theRulebookExample() {
            // 100 axemen against 100 spearmen behind wall 5: A = 4000, D = 2212.5
            assertThat(withBanners(0, 0).attackerLosses()).isEqualTo(Map.of(AXEMAN, 41));
            // War III: A = 4400 → (2212.5 / 4400)^1.5 = 0.3566
            assertThat(withBanners(0.10, 0).attackerLosses()).isEqualTo(Map.of(AXEMAN, 36));
            // Bulwark III: D = 2433.75 → 0.4746
            assertThat(withBanners(0, 0.10).attackerLosses()).isEqualTo(Map.of(AXEMAN, 47));
        }

        @Test
        void warComesOnTopOfLuckAndBulwarkOnTopOfTheWall() {
            var result =
                    Combat.fight(
                            data,
                            BattleInput.attack(axemen)
                                    .defenders(spearmen)
                                    .wall(5)
                                    .luck(0.2)
                                    .defenderLevels(List.of(UnitLevels.NONE))
                                    .attackBonus(0.06)
                                    .defenceBonus(0.03)
                                    .build());
            assertThat(result.mainBattle().attack()).isCloseTo(4000 * 1.2 * 1.06, FIVE_DECIMALS);
            assertThat(result.mainBattle().defense()).isCloseTo(2212.5 * 1.03, FIVE_DECIMALS);
        }

        @Test
        void aBannerCanTurnTheBattle() {
            // 20 axemen (A = 800) against 50 spearmen (D = 770): won without, lost behind a
            // Bulwark III (847)
            var attack = Map.of(AXEMAN, 20);
            var stacks = List.of(Map.of(SPEARMAN, 50));
            assertThat(fight(attack, stacks, 0, 0, 0).attackerWon()).isTrue();
            assertThat(fight(attack, stacks, 0, 0, 0.10).attackerWon()).isFalse();
        }

        @Test
        void bonusesOutsideTheirRangeAreRejected() {
            var levels = List.of(UnitLevels.NONE);
            assertThatThrownBy(
                            () ->
                                    BattleInput.attack(axemen)
                                            .defenders(spearmen)
                                            .defenderLevels(levels)
                                            .attackBonus(-0.1)
                                            .build())
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(
                            () ->
                                    BattleInput.attack(axemen)
                                            .defenders(spearmen)
                                            .defenderLevels(levels)
                                            .defenceBonus(1)
                                            .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void bannerThievesAddNothingAndDieLikeTheOthers() {
            var withThieves = new EnumMap<UnitType, Integer>(UnitType.class);
            withThieves.put(AXEMAN, 100);
            withThieves.put(UnitType.BANNER_THIEF, 20);
            var result = fight(withThieves, spearmen, 5, 0, 0);
            assertThat(result.mainBattle().attack()).isEqualTo(4000.0);
            // the axemen's loss ratio 0.411: 20 × 0.411 = 8.2
            assertThat(result.attackerLosses()).containsEntry(UnitType.BANNER_THIEF, 8);
        }

        @Test
        void thievesAloneFightNoBattle() {
            var result = fight(Map.of(UnitType.BANNER_THIEF, 5), spearmen, 0, 0, 0);
            assertThat(result.mainBattle()).isNull();
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(UnitType.BANNER_THIEF, 5));
        }

        private BattleResult withBanners(double war, double bulwark) {
            return fight(axemen, spearmen, 5, war, bulwark);
        }

        private BattleResult fight(
                Map<UnitType, Integer> attacker,
                List<Map<UnitType, Integer>> stacks,
                int wall,
                double war,
                double bulwark) {
            return Combat.fight(
                    data,
                    BattleInput.attack(attacker)
                            .defenders(stacks)
                            .wall(wall)
                            .attackBonus(war)
                            .defenceBonus(bulwark)
                            .build());
        }
    }

    /** The night bonus (1.4, 02-game-rules §8.1): a multiplier on D after the Bulwark. */
    @Nested
    class NightBonusInBattle {

        private final Map<UnitType, Integer> axemen = Map.of(AXEMAN, 100);
        private final List<Map<UnitType, Integer>> spearmen = List.of(Map.of(SPEARMAN, 100));

        @Test
        void theRulebookExample() {
            // 100 axemen against 100 spearmen behind wall 5: D = 2212.5 by day, 4425 at night
            assertThat(fight(axemen, spearmen, 5, 0, 1).attackerWon()).isTrue();
            var night = fight(axemen, spearmen, 5, 0, 2);
            assertThat(night.mainBattle().defense()).isEqualTo(4425.0);
            assertThat(night.attackerWon()).isFalse();
            // the defender wins: (4000 / 4425)^1.5 = 0.8594 of the spearmen die
            assertThat(night.defenderLosses()).containsExactly(Map.of(SPEARMAN, 86));
        }

        @Test
        void itComesOnTopOfTheBulwark() {
            var result = fight(axemen, spearmen, 5, 0.10, 1.5);
            assertThat(result.mainBattle().defense()).isCloseTo(2212.5 * 1.1 * 1.5, FIVE_DECIMALS);
            assertThat(result.mainBattle().attack()).isEqualTo(4000.0);
        }

        @Test
        void anEmptyVillageGetsItOnItsBaseDefenceToo() {
            // 1 axeman (A = 40) against nobody (D = 20): won by day, a tie at night (defender)
            assertThat(fight(Map.of(AXEMAN, 1), List.of(), 0, 0, 1).attackerWon()).isTrue();
            assertThat(fight(Map.of(AXEMAN, 1), List.of(), 0, 0, 2).attackerWon()).isFalse();
        }

        @Test
        void aFactorBelowOneIsRejected() {
            assertThatThrownBy(() -> fight(axemen, spearmen, 0, 0, 0.5))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private BattleResult fight(
                Map<UnitType, Integer> attacker,
                List<Map<UnitType, Integer>> stacks,
                int wall,
                double bulwark,
                double night) {
            return Combat.fight(
                    data,
                    BattleInput.attack(attacker)
                            .defenders(stacks)
                            .wall(wall)
                            .defenceBonus(bulwark)
                            .nightFactor(night)
                            .build());
        }
    }

    /** Morale (1.6, 02-game-rules §8.2): a multiplier on A after the War banner, D untouched. */
    @Nested
    class MoraleInBattle {

        private final Map<UnitType, Integer> axemen = Map.of(AXEMAN, 100);
        private final List<Map<UnitType, Integer>> spearmen = List.of(Map.of(SPEARMAN, 100));

        @Test
        void itWeakensOnlyTheAttack() {
            // 100 axemen (A = 4000) against 100 spearmen behind wall 5 (D = 2212.5)
            var full = fight(axemen, 0, 0, 1);
            var half = fight(axemen, 0, 0, 0.5);
            assertThat(full.attackerWon()).isTrue();
            assertThat(half.mainBattle().attack()).isEqualTo(2000.0);
            assertThat(half.mainBattle().defense()).isEqualTo(2212.5);
            assertThat(half.mainBattle().effectiveWall())
                    .isEqualTo(full.mainBattle().effectiveWall());
            // the defender wins: (2000 / 2212.5)^1.5 = 0.8594 of the spearmen die
            assertThat(half.attackerWon()).isFalse();
            assertThat(half.defenderLosses()).containsExactly(Map.of(SPEARMAN, 86));
        }

        @Test
        void itComesAfterLuckAndTheWarBanner() {
            var result = fight(axemen, 0.1, 0.1, 0.48);
            assertThat(result.mainBattle().attack())
                    .isCloseTo(4000 * 1.1 * 1.1 * 0.48, FIVE_DECIMALS);
            assertThat(result.mainBattle().defense()).isEqualTo(2212.5);
        }

        @Test
        void ramsStillCountInFull() {
            // the rams lower the wall during the battle whatever the morale
            var attack = Map.of(AXEMAN, 1000, RAM, 20);
            var full = fight(attack, 0, 0, 1);
            var low = fight(attack, 0, 0, 0.3);
            assertThat(low.mainBattle().effectiveWall())
                    .isEqualTo(full.mainBattle().effectiveWall());
        }

        @Test
        void itScalesTheArchersWithTheRestAndLeavesTheShares() {
            // 1.6: 100 axemen + 50 mounted archers (A = 9500) against 100 spearmen, wall 0: the
            // defence is weighted by the class shares, which morale doesn't change (D = 1809.47)
            var mixed = Map.of(AXEMAN, 100, UnitType.MOUNTED_ARCHER, 50);
            var stacks = List.of(Map.of(SPEARMAN, 100));
            var full = Combat.fight(data, BattleInput.attack(mixed).defenders(stacks).build());
            var low =
                    Combat.fight(
                            data, BattleInput.attack(mixed).defenders(stacks).morale(0.3).build());
            assertThat(low.mainBattle().attack()).isCloseTo(9500 * 0.3, FIVE_DECIMALS);
            assertThat(low.mainBattle().defense()).isEqualTo(full.mainBattle().defense());
        }

        @Test
        void aMoraleOutsideZeroToOneIsRejected() {
            assertThatThrownBy(() -> fight(axemen, 0, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> fight(axemen, 0, 0, 1.01))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private BattleResult fight(
                Map<UnitType, Integer> attacker, double luck, double war, double morale) {
            return Combat.fight(
                    data,
                    BattleInput.attack(attacker)
                            .defenders(spearmen)
                            .wall(5)
                            .luck(luck)
                            .attackBonus(war)
                            .morale(morale)
                            .build());
        }
    }

    /** Paladin standards (1.4, 02-game-rules §21.18): only the units of their army or stack. */
    @Nested
    class StandardsInBattle {

        private StandardEffect standard(BannerType type, double bonus) {
            return new StandardEffect(type, 1, 1, bonus);
        }

        @Test
        void anAxeStandardRaisesOnlyTheAxemen() {
            var attack = Map.of(AXEMAN, 100, LIGHT_CAVALRY, 10);
            var stacks = List.of(Map.<UnitType, Integer>of());
            var plain = fight(attack, stacks, 0, StandardEffect.NONE, List.of(StandardEffect.NONE));
            var axe =
                    fight(
                            attack,
                            stacks,
                            0,
                            standard(BannerType.AXE_STANDARD, 0.15),
                            List.of(StandardEffect.NONE));
            // 100 axemen (4 000) gain 15 %, the light cavalry (1 200) nothing
            assertThat(plain.mainBattle().attack()).isCloseTo(5200, FIVE_DECIMALS);
            assertThat(axe.mainBattle().attack()).isCloseTo(4600 + 1200, FIVE_DECIMALS);
        }

        @Test
        void aSpearStandardRaisesOnlyTheSpearmenOfItsOwnStack() {
            // wall 0: 100 spearmen 1 500 against infantry, 20 swordsmen 1 000, base defence 20
            var attack = Map.of(AXEMAN, 100);
            List<Map<UnitType, Integer>> stacks =
                    List.of(Map.of(SPEARMAN, 100, SWORDSMAN, 20), Map.of(SPEARMAN, 100));
            var plain =
                    fight(
                            attack,
                            stacks,
                            0,
                            StandardEffect.NONE,
                            List.of(StandardEffect.NONE, StandardEffect.NONE));
            var spear =
                    fight(
                            attack,
                            stacks,
                            0,
                            StandardEffect.NONE,
                            List.of(
                                    standard(BannerType.SPEAR_STANDARD, 0.05),
                                    StandardEffect.NONE));
            assertThat(plain.mainBattle().defense()).isCloseTo(4020, FIVE_DECIMALS);
            assertThat(spear.mainBattle().defense()).isCloseTo(4020 + 75, FIVE_DECIMALS);
        }

        @Test
        void aRamStandardMakesTheRamsCountForMore() {
            var attack = Map.of(AXEMAN, 1000, RAM, 20);
            var stacks = List.of(Map.<UnitType, Integer>of());
            var plain =
                    fight(attack, stacks, 10, StandardEffect.NONE, List.of(StandardEffect.NONE));
            var ram =
                    fight(
                            attack,
                            stacks,
                            10,
                            standard(BannerType.RAM_STANDARD, 0.30),
                            List.of(StandardEffect.NONE));
            assertThat(plain.wallAfter()).isEqualTo(Siege.wallAfterRams(10, 20));
            // 20 rams count as 26
            assertThat(ram.wallAfter()).isEqualTo(Siege.wallAfterRams(10, 26));
            assertThat(ram.wallAfter()).isLessThan(plain.wallAfter());
        }

        @Test
        void aSiegeStandardMakesTheCatapultsCountForMore() {
            var attack = Map.of(AXEMAN, 1000, CATAPULT, 20);
            List<Map<UnitType, Integer>> stacks = List.of(Map.of());
            var siege =
                    Combat.fight(
                            data,
                            BattleInput.attack(attack)
                                    .defenders(stacks)
                                    .catapults(BuildingType.BARRACKS, 3)
                                    .defenderLevels(List.of(UnitLevels.NONE))
                                    .attackerStandard(standard(BannerType.SIEGE_STANDARD, 0.20))
                                    .defenderStandards(List.of(StandardEffect.NONE))
                                    .build());
            int min = Siege.catapultMinLevel(BuildingType.BARRACKS);
            // 20 catapults count as 24: barracks 3 falls to 0 instead of 1
            assertThat(siege.catapultTargetLevelAfter())
                    .isEqualTo(Siege.levelAfterCatapults(3, 24, min))
                    .isLessThan(Siege.levelAfterCatapults(3, 20, min));
        }

        @Test
        void oneStandardPerDefendingStack() {
            assertThatThrownBy(
                            () ->
                                    BattleInput.attack(Map.of(AXEMAN, 1))
                                            .defenders(List.of(Map.of(SPEARMAN, 1)))
                                            .defenderLevels(List.of(UnitLevels.NONE))
                                            .defenderStandards(List.of())
                                            .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private BattleResult fight(
                Map<UnitType, Integer> attacker,
                List<Map<UnitType, Integer>> stacks,
                int wall,
                StandardEffect attackerStandard,
                List<StandardEffect> defenderStandards) {
            return Combat.fight(
                    data,
                    BattleInput.attack(attacker)
                            .defenders(stacks)
                            .wall(wall)
                            .attackerStandard(attackerStandard)
                            .defenderStandards(defenderStandards)
                            .build());
        }
    }

    /** Unit levels (M30, 02-game-rules §5.4): each side fights with the stats of its levels. */
    @Nested
    class UnitLevelsInBattle {

        @Test
        void levelOneEverywhereIsTheBattleWithoutLevels() {
            var plain = fight(Map.of(AXEMAN, 20), List.of(Map.of(SPEARMAN, 50)), 0);
            var withNone =
                    Combat.fight(
                            data,
                            BattleInput.attack(Map.of(AXEMAN, 20))
                                    .defenders(List.of(Map.of(SPEARMAN, 50)))
                                    .defenderLevels(List.of(new UnitLevels(Map.of(SPEARMAN, 1))))
                                    .build());

            assertThat(withNone).isEqualTo(plain);
        }

        @Test
        void attackLevelRaisesTheAttackStrength() {
            // 20 axemen: level 1 attack 40 = 800, level 2 attack 44 = 880, level 3 attack 48 = 960
            assertThat(strength(Map.of(AXEMAN, 2), Map.of(AXEMAN, 1))).isEqualTo(800.0);
            assertThat(strength(Map.of(AXEMAN, 2), Map.of(AXEMAN, 2))).isEqualTo(880.0);
            assertThat(strength(Map.of(AXEMAN, 2), Map.of(AXEMAN, 3))).isEqualTo(960.0);
        }

        @Test
        void defenceLevelRaisesTheDefence() {
            // 50 spearmen against infantry: 15 / 17 / 18 each, plus the base defence 20
            assertThat(defence(1)).isEqualTo(770.0);
            assertThat(defence(2)).isEqualTo(870.0);
            assertThat(defence(3)).isEqualTo(920.0);
        }

        @Test
        void aLevelCanDecideTheBattle() {
            // A = 800 against D = 770 is won; spearmen on level 3 (D = 920) hold, axemen on level
            // 3 (A = 960) win again
            assertThat(fight(Map.of(AXEMAN, 20), List.of(Map.of(SPEARMAN, 50)), 0).attackerWon())
                    .isTrue();
            assertThat(
                            levelled(
                                            Map.of(AXEMAN, 20),
                                            UnitLevels.NONE,
                                            Map.of(SPEARMAN, 50),
                                            new UnitLevels(Map.of(SPEARMAN, 3)))
                                    .attackerWon())
                    .isFalse();
            assertThat(
                            levelled(
                                            Map.of(AXEMAN, 20),
                                            new UnitLevels(Map.of(AXEMAN, 3)),
                                            Map.of(SPEARMAN, 50),
                                            new UnitLevels(Map.of(SPEARMAN, 3)))
                                    .attackerWon())
                    .isTrue();
        }

        @Test
        void eachStackFightsWithItsOwnLevels() {
            // two stacks of 50 spearmen: the first on level 3 (18), the second on level 1 (15):
            // 50 × 18 + 50 × 15 + 20 = 1670
            var input =
                    BattleInput.attack(Map.of(AXEMAN, 100))
                            .defenders(List.of(Map.of(SPEARMAN, 50), Map.of(SPEARMAN, 50)))
                            .defenderLevels(
                                    List.of(new UnitLevels(Map.of(SPEARMAN, 3)), UnitLevels.NONE))
                            .build();

            assertThat(Combat.fight(data, input).mainBattle().defense()).isEqualTo(1670.0);
        }

        @Test
        void cavalryDefenceUsesTheCavalryValueOfTheLevel() {
            // 50 light cavalry attack 100 spearmen: cavalry defence 40 / 44 / 48
            var level3 =
                    levelled(
                            Map.of(LIGHT_CAVALRY, 50),
                            UnitLevels.NONE,
                            Map.of(SPEARMAN, 100),
                            new UnitLevels(Map.of(SPEARMAN, 3)));

            assertThat(level3.mainBattle().defense()).isEqualTo(4820.0); // 100 × 48 + 20
        }

        @Test
        void aLevelAboveTheMaximumCountsAsTheMaximum() {
            assertThat(strength(Map.of(AXEMAN, 2), Map.of(AXEMAN, 9))).isEqualTo(960.0);
        }

        @Test
        void unitsWithoutLevelsStayTheSame() {
            // a ram has no research steps: 100 rams attack with the table's 2 each
            assertThat(strength(Map.of(RAM, 10), Map.of(RAM, 3))).isEqualTo(200.0);
        }

        @Test
        void oneSetOfLevelsPerStack() {
            assertThatThrownBy(
                            () ->
                                    BattleInput.attack(Map.of(AXEMAN, 1))
                                            .defenders(List.of(Map.of(SPEARMAN, 1)))
                                            .defenderLevels(List.of())
                                            .build())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private double strength(Map<UnitType, Integer> tens, Map<UnitType, Integer> levels) {
            Map<UnitType, Integer> army = new EnumMap<>(UnitType.class);
            tens.forEach((type, count) -> army.put(type, count * 10));
            return levelled(army, new UnitLevels(levels), Map.of(), UnitLevels.NONE)
                    .mainBattle()
                    .attack();
        }

        private double defence(int spearmanLevel) {
            return levelled(
                            Map.of(AXEMAN, 1),
                            UnitLevels.NONE,
                            Map.of(SPEARMAN, 50),
                            new UnitLevels(Map.of(SPEARMAN, spearmanLevel)))
                    .mainBattle()
                    .defense();
        }

        private BattleResult levelled(
                Map<UnitType, Integer> attacker,
                UnitLevels attackerLevels,
                Map<UnitType, Integer> defender,
                UnitLevels defenderLevels) {
            return Combat.fight(
                    data,
                    BattleInput.attack(attacker)
                            .defenders(List.of(defender))
                            .attackerLevels(attackerLevels)
                            .defenderLevels(List.of(defenderLevels))
                            .build());
        }
    }

    /**
     * Archers (1.6, 02-game-rules §5.6, §8): a third attack class, met by the defenders' defence
     * against archers. The test vectors of §8 (luck 0).
     */
    @Nested
    class Archers {

        @Test
        void mountedArchersMeetTheSpearmensWeakArcherDefence() {
            // A = 50 × 110 = 5500; D = 100 × 20 + 20 = 2020 (light cavalry would meet 40 each)
            BattleResult result =
                    fight(Map.of(UnitType.MOUNTED_ARCHER, 50), List.of(Map.of(SPEARMAN, 100)), 0);

            var battle = result.mainBattle();
            assertThat(battle.attack()).isEqualTo(5500.0);
            assertThat(battle.defense()).isEqualTo(2020.0);
            assertThat(battle.attackerLossRatio()).isCloseTo(0.22258, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(UnitType.MOUNTED_ARCHER, 11));
            assertThat(result.defenderLosses()).containsExactly(Map.of(SPEARMAN, 100));
        }

        @Test
        void swordsmenHoldBetterAgainstArchers() {
            // D = 100 × 40 + 20 = 4020
            BattleResult result =
                    fight(Map.of(UnitType.MOUNTED_ARCHER, 50), List.of(Map.of(SWORDSMAN, 100)), 0);

            assertThat(result.mainBattle().defense()).isEqualTo(4020.0);
            assertThat(result.mainBattle().attackerLossRatio()).isCloseTo(0.62488, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(UnitType.MOUNTED_ARCHER, 31));
        }

        @Test
        void footArchersAttackWeakly() {
            // A = 100 × 15 = 1500 against D = 2020: the spearmen hold and lose (1500/2020)^1.5
            BattleResult result =
                    fight(Map.of(UnitType.ARCHER, 100), List.of(Map.of(SPEARMAN, 100)), 0);

            assertThat(result.mainBattle().attackerWon()).isFalse();
            assertThat(result.mainBattle().defenderLossRatio()).isCloseTo(0.63990, FIVE_DECIMALS);
            assertThat(result.defenderLosses()).containsExactly(Map.of(SPEARMAN, 64));
        }

        @Test
        void aMixedArmySplitsTheDefenceIntoThreeShares() {
            // Infantry 4000, archers 5500 of 9500: a spearman defends
            // (15 × 4000 + 20 × 5500) / 9500 = 17.8947…, so D = 1789.47… + 20
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 100, UnitType.MOUNTED_ARCHER, 50),
                            List.of(Map.of(SPEARMAN, 100)),
                            0);

            assertThat(result.mainBattle().attack()).isEqualTo(9500.0);
            assertThat(result.mainBattle().defense())
                    .isCloseTo(17_000_000.0 / 9500 + 20, FIVE_DECIMALS);
            assertThat(result.attackerLosses())
                    .isEqualTo(Map.of(AXEMAN, 8, UnitType.MOUNTED_ARCHER, 4));
        }

        @Test
        void archersDefendWithTheirInfantryAndCavalryValues() {
            // no archer attacks, so their weak 5 against archers doesn't count: D = 100 × 50 + 20
            BattleResult result =
                    fight(Map.of(AXEMAN, 100), List.of(Map.of(UnitType.ARCHER, 100)), 0);

            assertThat(result.mainBattle().defense()).isEqualTo(5020.0);
            assertThat(result.mainBattle().attackerWon()).isFalse();
            assertThat(result.defenderLosses()).containsExactly(Map.of(UnitType.ARCHER, 71));
        }

        @Test
        void withoutArchersInTheAttackTheBattleIsAsBefore() {
            // the rulebook's mixed army (§8) fights exactly as before 1.6 against archers' values
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 100, LIGHT_CAVALRY, 50),
                            List.of(Map.of(SPEARMAN, 100, SWORDSMAN, 100)),
                            0);

            assertThat(result.mainBattle().defense()).isEqualTo(6220.0);
        }
    }

    /** The test vectors of 02-game-rules §7.1 and §8 (luck 0). */
    @Nested
    class RulebookVectors {

        @Test
        void axemenAgainstSpearmenBehindWall5() {
            BattleResult result = fight(Map.of(AXEMAN, 100), List.of(Map.of(SPEARMAN, 100)), 5);

            var battle = result.mainBattle();
            assertThat(battle.attack()).isEqualTo(4000.0);
            assertThat(battle.defense()).isEqualTo(2212.5); // (1500 + 20 + 250) × 1.25
            assertThat(battle.effectiveWall()).isEqualTo(5);
            assertThat(battle.attackerWon()).isTrue();
            assertThat(battle.attackerLossRatio()).isCloseTo(0.41137, FIVE_DECIMALS);
            assertThat(battle.defenderLossRatio()).isEqualTo(1.0);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 41));
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 59));
            assertThat(result.defenderLosses()).containsExactly(Map.of(SPEARMAN, 100));
            assertThat(result.attackerWon()).isTrue();
        }

        @Test
        void lightCavalryMeetsTheCavalryDefence() {
            BattleResult result =
                    fight(Map.of(LIGHT_CAVALRY, 50), List.of(Map.of(SPEARMAN, 100)), 0);

            var battle = result.mainBattle();
            assertThat(battle.attack()).isEqualTo(6000.0);
            assertThat(battle.defense()).isEqualTo(4020.0); // spearmen defend 40 against cavalry
            assertThat(battle.attackerLossRatio()).isCloseTo(0.54842, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(LIGHT_CAVALRY, 27));
            assertThat(result.defenderLosses()).containsExactly(Map.of(SPEARMAN, 100));
        }

        @Test
        void tooFewAxemenAgainstSwordsmen() {
            BattleResult result = fight(Map.of(AXEMAN, 20), List.of(Map.of(SWORDSMAN, 100)), 0);

            var battle = result.mainBattle();
            assertThat(battle.attack()).isEqualTo(800.0);
            assertThat(battle.defense()).isEqualTo(5020.0);
            assertThat(battle.attackerWon()).isFalse();
            assertThat(battle.attackerLossRatio()).isEqualTo(1.0);
            assertThat(battle.defenderLossRatio()).isCloseTo(0.06362, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 20));
            assertThat(result.attackerSurvivors()).isEmpty();
            assertThat(result.defenderLosses()).containsExactly(Map.of(SWORDSMAN, 6));
            assertThat(result.attackerWon()).isFalse();
        }

        @Test
        void tenScoutsAgainstFour() {
            BattleResult result = fight(Map.of(SCOUT, 10), List.of(Map.of(SCOUT, 4)), 0);

            assertThat(result.scouting())
                    .isEqualTo(new BattleResult.Scouting(10, 4, 3, true)); // round(10 × 0.4^1.5)
            assertThat(result.mainBattle()).isNull();
            assertThat(result.attackerLosses()).isEqualTo(Map.of(SCOUT, 3));
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(SCOUT, 7));
            assertThat(result.defenderLosses()).containsExactly(Map.of());
        }
    }

    @Nested
    class MainBattleRules {

        @Test
        void aMixedArmySplitsTheDefenceByItsShares() {
            // Infantry 4000, cavalry 6000: 40 % / 60 %. Spearman 15 × 0.4 + 40 × 0.6 = 30,
            // swordsman 50 × 0.4 + 20 × 0.6 = 32.
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 100, LIGHT_CAVALRY, 50),
                            List.of(Map.of(SPEARMAN, 100, SWORDSMAN, 100)),
                            0);

            assertThat(result.mainBattle().attack()).isEqualTo(10_000.0);
            assertThat(result.mainBattle().defense()).isEqualTo(6220.0); // exact, no 6220.000…1
            assertThat(result.mainBattle().attackerLossRatio()).isCloseTo(0.49055, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 49, LIGHT_CAVALRY, 25));
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 51, LIGHT_CAVALRY, 25));
        }

        @Test
        void luckChangesOnlyTheAttack() {
            BattleResult good =
                    fight(Map.of(LIGHT_CAVALRY, 50), List.of(Map.of(SPEARMAN, 100)), 0, 0.25);
            BattleResult bad =
                    fight(Map.of(LIGHT_CAVALRY, 50), List.of(Map.of(SPEARMAN, 100)), 0, -0.25);

            assertThat(good.mainBattle().attack()).isEqualTo(7500.0);
            assertThat(good.mainBattle().defense()).isEqualTo(4020.0);
            assertThat(good.attackerLosses()).isEqualTo(Map.of(LIGHT_CAVALRY, 20));
            assertThat(bad.mainBattle().attack()).isEqualTo(4500.0);
            assertThat(bad.mainBattle().defense()).isEqualTo(4020.0);
            assertThat(bad.attackerLosses()).isEqualTo(Map.of(LIGHT_CAVALRY, 42));
        }

        @Test
        void aTieGoesToTheDefender() {
            // 2 spearmen: A = 20, an empty village without wall: D = 20.
            BattleResult result = fight(Map.of(SPEARMAN, 2), List.of(), 0);

            assertThat(result.mainBattle().attack()).isEqualTo(result.mainBattle().defense());
            assertThat(result.attackerWon()).isFalse();
            assertThat(result.attackerLosses()).isEqualTo(Map.of(SPEARMAN, 2));
        }

        @Test
        void anEmptyVillageStillHasItsBaseDefence() {
            BattleResult result = fight(Map.of(SPEARMAN, 10), List.of(), 0);

            assertThat(result.mainBattle().defense()).isEqualTo(20.0);
            assertThat(result.attackerWon()).isTrue();
            // (20 / 100)^1.5 = 0.0894 → round(0.894) = 1
            assertThat(result.attackerLosses()).isEqualTo(Map.of(SPEARMAN, 1));
            assertThat(result.defenderLosses()).isEmpty();
        }

        @Test
        void withoutAnyAttackTheDefenderLosesNothing() {
            // Luck −1 is the only way to get A = 0 into a main battle: ratio (0 / D)^1.5 = 0.
            BattleResult result =
                    fight(Map.of(AXEMAN, 20), List.of(Map.of(SWORDSMAN, 100)), 0, -1.0);

            assertThat(result.mainBattle().attack()).isZero();
            assertThat(result.mainBattle().defenderLossRatio()).isZero();
            assertThat(result.defenderLosses()).containsExactly(Map.of());
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 20));
        }

        @Test
        void everyStackDefendsAndLosesWithTheSameRatioRoundedPerStack() {
            // The same 100 swordsmen as in the rulebook vector, but in five stacks (home + four
            // supports): D and the ratio 0.06362 stay, each stack loses round(1.27) = 1, so 5 in
            // total instead of 6.
            var stack = Map.of(SWORDSMAN, 20);
            BattleResult result =
                    fight(Map.of(AXEMAN, 20), List.of(stack, stack, stack, stack, stack), 0);

            assertThat(result.mainBattle().defense()).isEqualTo(5020.0);
            assertThat(result.defenderLosses())
                    .containsExactly(
                            Map.of(SWORDSMAN, 1),
                            Map.of(SWORDSMAN, 1),
                            Map.of(SWORDSMAN, 1),
                            Map.of(SWORDSMAN, 1),
                            Map.of(SWORDSMAN, 1));
        }

        @Test
        void allDefendingTypesCountAndDieIncludingScoutsAndNobles() {
            // 10 × 15 + 5 × 2 + 1 × 100 + 10 × 50 = 760 → D = 780
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 1000),
                            List.of(
                                    Map.of(SPEARMAN, 10, SCOUT, 5, NOBLE, 1),
                                    Map.of(SWORDSMAN, 10)),
                            0);

            assertThat(result.mainBattle().defense()).isEqualTo(780.0);
            assertThat(result.defenderLosses())
                    .containsExactly(
                            Map.of(SPEARMAN, 10, SCOUT, 5, NOBLE, 1), Map.of(SWORDSMAN, 10));
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 3));
        }

        @Test
        void anEmptyStackKeepsItsPlaceInTheLosses() {
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 1000),
                            List.of(Map.of(SPEARMAN, 10), Map.of(), Map.of(SWORDSMAN, 10)),
                            0);

            assertThat(result.defenderLosses())
                    .containsExactly(Map.of(SPEARMAN, 10), Map.of(), Map.of(SWORDSMAN, 10));
        }

        @Test
        void survivingNoblesAreReported() {
            BattleResult result = fight(Map.of(AXEMAN, 1000, NOBLE, 1), List.of(), 0);

            assertThat(result.attackerWon()).isTrue();
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 1000, NOBLE, 1));
            assertThat(result.attackerLosses()).isEmpty();
        }
    }

    @Nested
    class ScoutingPhase {

        @Test
        void scoutsAgainstAsManyScoutsAllDie() {
            BattleResult result = fight(Map.of(SCOUT, 4), List.of(Map.of(SCOUT, 4)), 0);

            assertThat(result.scouting()).isEqualTo(new BattleResult.Scouting(4, 4, 4, false));
            assertThat(result.attackerSurvivors()).isEmpty();
            assertThat(result.defenderLosses()).containsExactly(Map.of()); // never in scouting
        }

        @Test
        void scoutsAgainstNoScoutsLoseNothing() {
            BattleResult result = fight(Map.of(SCOUT, 5), List.of(Map.of(SPEARMAN, 100)), 0);

            assertThat(result.scouting()).isEqualTo(new BattleResult.Scouting(5, 0, 0, true));
            assertThat(result.attackerLosses()).isEmpty();
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(SCOUT, 5));
            assertThat(result.defenderLosses()).containsExactly(Map.of());
        }

        @Test
        void defendingScoutsOfAllStacksCount() {
            BattleResult result =
                    fight(Map.of(SCOUT, 5), List.of(Map.of(SCOUT, 3), Map.of(SCOUT, 2)), 0);

            assertThat(result.scouting().defending()).isEqualTo(5);
            assertThat(result.scouting().succeeded()).isFalse();
            assertThat(result.defenderLosses()).containsExactly(Map.of(), Map.of());
        }

        @Test
        void survivingScoutsShareTheAttackersLossRatio() {
            // Scouting: 10 vs 4 → 3 die, 7 go on. Main battle: D = (1500 + 4 × 2 + 20 + 250) ×
            // 1.25 = 2222.5, ratio 0.41416 → round(7 × 0.41416) = 3 more scouts, 41 axemen.
            BattleResult result =
                    fight(
                            Map.of(SCOUT, 10, AXEMAN, 100),
                            List.of(Map.of(SCOUT, 4, SPEARMAN, 100)),
                            5);

            assertThat(result.scouting()).isEqualTo(new BattleResult.Scouting(10, 4, 3, true));
            assertThat(result.mainBattle().defense()).isEqualTo(2222.5);
            assertThat(result.mainBattle().attackerLossRatio()).isCloseTo(0.41416, FIVE_DECIMALS);
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 41, SCOUT, 6));
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 59, SCOUT, 4));
            assertThat(result.defenderLosses()).containsExactly(Map.of(SCOUT, 4, SPEARMAN, 100));
        }

        @Test
        void scoutsThatSucceededStillDieWhenTheBattleIsLost() {
            BattleResult result =
                    fight(
                            Map.of(SCOUT, 10, AXEMAN, 20),
                            List.of(Map.of(SCOUT, 4, SWORDSMAN, 100)),
                            0);

            assertThat(result.scouting().succeeded()).isTrue();
            assertThat(result.attackerWon()).isFalse();
            assertThat(result.attackerLosses()).isEqualTo(Map.of(AXEMAN, 20, SCOUT, 10));
            assertThat(result.attackerSurvivors()).isEmpty();
        }
    }

    @Nested
    class SiegeInBattle {

        @Test
        void tenRamsAloneLoseAgainstAnEmptyVillage() {
            // A = 10 × 2 = 20 is not more than the base defence 20.
            BattleResult result = fight(Map.of(RAM, 10), List.of(), 0);

            assertThat(result.attackerWon()).isFalse();
            assertThat(result.attackerLosses()).isEqualTo(Map.of(RAM, 10));
        }

        @Test
        void halfTheRamsLowerTheWallDuringTheBattle() {
            // 20 rams → 10 for the battle: wall 3 → 1, D = (20 + 50) × 1.05 = 73.5 > A = 40.
            BattleResult result = fight(Map.of(RAM, 20), List.of(), 3);

            assertThat(result.mainBattle().effectiveWall()).isEqualTo(1);
            assertThat(result.mainBattle().defense()).isCloseTo(73.5, within(1e-9));
            assertThat(result.attackerWon()).isFalse();
            assertThat(result.wallAfter()).isEqualTo(3); // a lost battle leaves the wall
        }

        @Test
        void ramsOnlyBreakTheWallWhenTheyWin() {
            // 25 rams for the battle take wall 3 to 0 → D = 20 < A = 100; 4 rams die
            // (round(50 × 0.0894)); the 46 survivors take the wall down for good.
            BattleResult result = fight(Map.of(RAM, 50), List.of(), 3);

            assertThat(result.mainBattle().effectiveWall()).isZero();
            assertThat(result.attackerWon()).isTrue();
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(RAM, 46));
            assertThat(result.wallAfter()).isZero();
        }

        @Test
        void theSurvivingRamsDecideThePermanentDamage() {
            // 7 rams for the battle: wall 5 → 4, D = (1500 + 20 + 200) × 1.2 = 2064; 5 of 14 rams
            // die, 9 survive: wall 5 needs 7 → 4.
            BattleResult result =
                    fight(Map.of(AXEMAN, 100, RAM, 14), List.of(Map.of(SPEARMAN, 100)), 5);

            assertThat(result.mainBattle().effectiveWall()).isEqualTo(4);
            assertThat(result.mainBattle().defense()).isCloseTo(2064.0, within(1e-9));
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 63, RAM, 9));
            assertThat(result.wallAfter()).isEqualTo(4);
        }

        @Test
        void catapultsDamageTheirTarget() {
            // Rulebook reference: Town Hall 20 with 100 catapults → 18.
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 1000, CATAPULT, 100),
                            List.of(),
                            0,
                            BuildingType.HEADQUARTERS,
                            20);

            assertThat(result.attackerSurvivors().get(CATAPULT)).isEqualTo(100);
            assertThat(result.catapultTargetLevelAfter()).isEqualTo(18);
            assertThat(result.wallAfter()).isZero();
        }

        @Test
        void catapultsRespectTheMinimumLevel() {
            BattleResult result =
                    fight(Map.of(AXEMAN, 1000, CATAPULT, 100), List.of(), 0, BuildingType.FARM, 3);

            assertThat(result.catapultTargetLevelAfter()).isEqualTo(1);
        }

        @Test
        void catapultsAimedAtTheWallComeAfterTheRams() {
            // Wall 10: 20 surviving rams → 9 (12 used, 8 left over), then 50 catapults → 7
            // (22 + 20 used).
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 1000, RAM, 20, CATAPULT, 50),
                            List.of(),
                            10,
                            BuildingType.WALL,
                            10);

            assertThat(result.mainBattle().effectiveWall()).isEqualTo(10); // 10 rams: not enough
            assertThat(result.attackerSurvivors())
                    .isEqualTo(Map.of(AXEMAN, 998, RAM, 20, CATAPULT, 50));
            assertThat(result.wallAfter()).isEqualTo(7);
            assertThat(result.catapultTargetLevelAfter()).isEqualTo(7);
        }

        @Test
        void aWallTargetFollowsTheRamsWhenNoCatapultSurvives() {
            // Ratio 0.519: the one catapult dies, 14 of 30 rams survive and take wall 3 to 0.
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 200, RAM, 30, CATAPULT, 1),
                            List.of(Map.of(SPEARMAN, 350)),
                            3,
                            BuildingType.WALL,
                            3);

            assertThat(result.mainBattle().defense()).isEqualTo(5270.0);
            assertThat(result.attackerSurvivors()).isEqualTo(Map.of(AXEMAN, 96, RAM, 14));
            assertThat(result.wallAfter()).isZero();
            assertThat(result.catapultTargetLevelAfter()).isZero();
        }

        @Test
        void aLostBattleLeavesWallAndTarget() {
            BattleResult result =
                    fight(
                            Map.of(AXEMAN, 20, RAM, 10, CATAPULT, 10),
                            List.of(Map.of(SWORDSMAN, 100)),
                            2,
                            BuildingType.HEADQUARTERS,
                            5);

            assertThat(result.attackerWon()).isFalse();
            assertThat(result.mainBattle().effectiveWall()).isEqualTo(1);
            assertThat(result.wallAfter()).isEqualTo(2);
            assertThat(result.catapultTargetLevelAfter()).isEqualTo(5);
            assertThat(result.defenderLosses()).containsExactly(Map.of(SWORDSMAN, 20));
        }
    }

    @Nested
    class Helpers {

        @Test
        void defenseStrengthFollowsTheFormula() {
            assertThat(Combat.defenseStrength(0, 0)).isEqualTo(20.0);
            assertThat(Combat.defenseStrength(1500, 5)).isEqualTo(2212.5);
            assertThat(Combat.defenseStrength(0, 20)).isCloseTo(2040.0, within(1e-9));
        }

        @Test
        void lossRatioIsZeroWithoutStrengthAndOneForEqualSides() {
            assertThat(Combat.lossRatio(0, 5020)).isZero();
            assertThat(Combat.lossRatio(20, 20)).isEqualTo(1.0);
            assertThat(Combat.lossRatio(4, 10)).isCloseTo(0.25298, FIVE_DECIMALS);
            assertThatThrownBy(() -> Combat.lossRatio(0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Combat.lossRatio(30, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void luckIsUniformInTheRangeWithOneDraw() {
            var random = FixedRandomSource.sequence(0.0, 0.5, 0.75);

            assertThat(Combat.drawLuck(random, 0.25)).isEqualTo(-0.25);
            assertThat(Combat.drawLuck(random, 0.25)).isZero();
            assertThat(Combat.drawLuck(random, 0.25)).isEqualTo(0.125);
            assertThat(Combat.drawLuck(FixedRandomSource.constant(0.9), 0)).isZero();
            assertThatThrownBy(() -> Combat.drawLuck(random, -0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Input {

        @Test
        void unitMapsAreCopiedWithoutZeros() {
            var attacker = new EnumMap<UnitType, Integer>(Map.of(AXEMAN, 5, RAM, 0));
            var stack = new EnumMap<UnitType, Integer>(Map.of(SPEARMAN, 0));

            var input = BattleInput.attack(attacker).defenders(List.of(stack)).build();
            attacker.put(AXEMAN, 99);

            assertThat(input.attacker()).isEqualTo(Map.of(AXEMAN, 5));
            assertThat(input.defenderStacks()).containsExactly(Map.of());
            assertThatThrownBy(() -> input.attacker().put(SPEARMAN, 1))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void whatTheBuilderLeavesOutIsTheNeutralBattle() {
            // the battle setup (1.6): nobody defending, wall 0, luck 0, no levels, banners,
            // standards, night bonus or morale; levels and standards default to none for each stack
            var stacks = List.of(Map.of(SPEARMAN, 10), Map.of(SWORDSMAN, 5));

            assertThat(BattleInput.attack(Map.of(AXEMAN, 10)).defenders(stacks).build())
                    .isEqualTo(
                            new BattleInput(
                                    Map.of(AXEMAN, 10),
                                    stacks,
                                    0,
                                    null,
                                    0,
                                    0,
                                    UnitLevels.NONE,
                                    List.of(UnitLevels.NONE, UnitLevels.NONE),
                                    0,
                                    0,
                                    StandardEffect.NONE,
                                    List.of(StandardEffect.NONE, StandardEffect.NONE),
                                    1,
                                    1));
            assertThat(BattleInput.attack(Map.of(AXEMAN, 10)).build().defenderStacks()).isEmpty();
            assertThat(
                            BattleInput.attack(Map.of(AXEMAN, 10))
                                    .defender(Map.of(SPEARMAN, 10))
                                    .build())
                    .isEqualTo(
                            BattleInput.attack(Map.of(AXEMAN, 10))
                                    .defenders(List.of(Map.of(SPEARMAN, 10)))
                                    .build());
        }

        @Test
        void invalidInputsAreRejected() {
            var axemen = Map.of(AXEMAN, 10);
            var catapults = Map.of(CATAPULT, 10);
            var noStacks = List.<Map<UnitType, Integer>>of();

            assertInvalid(() -> BattleInput.attack(Map.of()).defenders(noStacks).build());
            assertInvalid(() -> BattleInput.attack(Map.of(AXEMAN, 0)).defenders(noStacks).build());
            assertInvalid(() -> BattleInput.attack(Map.of(AXEMAN, -1)).defenders(noStacks).build());
            assertInvalid(
                    () -> BattleInput.attack(axemen).defenders(List.of(Map.of(SCOUT, -1))).build());
            assertInvalid(() -> BattleInput.attack(axemen).defenders(noStacks).wall(-1).build());
            assertInvalid(() -> BattleInput.attack(catapults).defenders(noStacks).build());
            assertInvalid(
                    () ->
                            BattleInput.attack(axemen)
                                    .defenders(noStacks)
                                    .catapults(null, 3)
                                    .build());
            assertInvalid(
                    () ->
                            BattleInput.attack(catapults)
                                    .defenders(noStacks)
                                    .wall(5)
                                    .catapults(BuildingType.WALL, 4)
                                    .build());
            assertInvalid(
                    () ->
                            BattleInput.attack(catapults)
                                    .defenders(noStacks)
                                    .catapults(BuildingType.HEADQUARTERS, -1)
                                    .build());
            assertInvalid(() -> BattleInput.attack(axemen).defenders(noStacks).luck(1.01).build());
            assertInvalid(
                    () -> BattleInput.attack(axemen).defenders(noStacks).luck(Double.NaN).build());
        }

        private void assertInvalid(Runnable create) {
            assertThatThrownBy(create::run).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private BattleResult fight(
            Map<UnitType, Integer> attacker, List<Map<UnitType, Integer>> stacks, int wall) {
        return fight(attacker, stacks, wall, 0);
    }

    private BattleResult fight(
            Map<UnitType, Integer> attacker,
            List<Map<UnitType, Integer>> stacks,
            int wall,
            double luck) {
        return Combat.fight(
                data, BattleInput.attack(attacker).defenders(stacks).wall(wall).luck(luck).build());
    }

    private BattleResult fight(
            Map<UnitType, Integer> attacker,
            List<Map<UnitType, Integer>> stacks,
            int wall,
            BuildingType catapultTarget,
            int catapultTargetLevel) {
        return Combat.fight(
                data,
                BattleInput.attack(attacker)
                        .defenders(stacks)
                        .wall(wall)
                        .catapults(catapultTarget, catapultTargetLevel)
                        .build());
    }
}
