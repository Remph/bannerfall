import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import {
  npcPersonalities,
  useAdminWorldSettings,
  useNpcLords,
  useSetNpcLordGuild,
  useSpawnNpcLord,
  type NpcLord,
  type NpcPersonality,
} from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon, type IconName } from '../../components/Icon';
import { MoreMenu } from '../../components/MoreMenu';
import { Sheet } from '../../components/Sheet';
import { tierName } from '../../game/banners';
import { formatDateTime, formatDecimal, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './AdminPage.module.css';
import { guildChoices, lordGuilds, type GuildChoice } from './lordGuilds';
import { mixChanges, percentOf, type LordLearning } from './lordLearning';

const choiceIcons: Record<GuildChoice['action'], IconName> = {
  JOIN: 'guild',
  LEAVE: 'close',
  FOUND: 'plus',
  AUTO: 'returning',
};

type Equipped = NonNullable<NpcLord['bannerVillages'][number]['banner']>;

/**
 * The NPC lords of the world, and a button per personality to spawn another one. While lord guilds
 * are on (09-npc-lords §13.7) also the lord guilds, and a "…" per lord to place it in one. While
 * banners are on, each lord's paladin level, equipped banners and stash (09-npc-lords §14.8). While a
 * lord has learned something, how much each lesson moved its troops and its wall goals (§15.5).
 */
export function NpcLordsCard() {
  const { t, language } = useI18n();
  const labels = t.admin.lords;
  const lords = useNpcLords();
  const spawn = useSpawnNpcLord();
  const place = useSetNpcLordGuild();
  const settings = useAdminWorldSettings().data;
  // there is no map before the world opens (M14g): the lords are created with it
  const pregame = settings?.phase === 'PREGAME';
  const guildsOn = settings !== undefined && settings.guildsEnabled && settings.npcGuildsEnabled;
  const maxMembers = settings === undefined ? 0 : Math.min(settings.npcGuildMaxMembers, settings.guildMaxMembers);
  const guilds = lordGuilds(lords.data ?? []);
  const bannersOn = settings !== undefined && settings.bannersEnabled;
  const lordBannersOn = bannersOn && settings.npcBannersEnabled;

  /** While lord banners are off, only a lord that holds something gets the banner lines. */
  function showsBanners(lord: NpcLord): boolean {
    return bannersOn && (lordBannersOn || lord.stash.length > 0 || lord.bannerVillages.length > 0);
  }

  /** "Harvest banner II (works at I)". */
  function equipped(item: Equipped): string {
    const name = `${t.banners.types[item.type]} ${tierName(item.tier)}`;
    if (item.effectiveTier === 0) {
      return `${name} (${labels.asleep})`;
    }
    return item.effectiveTier < item.tier ? `${name} (${labels.worksAt(tierName(item.effectiveTier))})` : name;
  }

  function stash(lord: NpcLord): string {
    if (lord.stash.length === 0) {
      return labels.stashEmpty;
    }
    return labels.stash(
      lord.stash
        .map((entry) => labels.times(`${t.banners.types[entry.type]} ${tierName(entry.tier)}`, entry.count))
        .join(', '),
    );
  }

  function choiceLabel(choice: GuildChoice): string {
    switch (choice.action) {
      case 'JOIN':
        return labels.moveTo(choice.guild?.name ?? '', choice.guild?.tag ?? '');
      case 'LEAVE':
        return labels.takeOut;
      case 'FOUND':
        return labels.found;
      case 'AUTO':
        return labels.letChoose;
    }
  }

  return (
    <Card title={labels.title}>
      <div className={styles.stack}>
        {pregame && <p className={styles.hint}>{labels.pregame}</p>}
        <div className={styles.spawn} hidden={pregame}>
          <span>{labels.spawn}</span>
          {npcPersonalities.map((personality: NpcPersonality) => (
            <Button
              key={personality}
              type="button"
              variant="secondary"
              size="small"
              disabled={spawn.isPending}
              onClick={() => spawn.mutate(personality)}
            >
              {labels.personalities[personality]}
            </Button>
          ))}
        </div>
        {spawn.isError && <ErrorMessage>{errorMessage(t, spawn.error)}</ErrorMessage>}
        {lords.isError && <ErrorMessage>{errorMessage(t, lords.error)}</ErrorMessage>}
        {lords.isPending && <p>{t.common.loading}</p>}
        {!pregame && lords.data?.length === 0 && <p className={styles.hint}>{labels.empty}</p>}
        {!pregame && lords.data !== undefined && lords.data.length > 0 && (
          <section className={styles.section} aria-label={labels.guilds}>
            <h3 className={styles.subtitle}>{labels.guilds}</h3>
            {!guildsOn && <p className={styles.hint}>{labels.guildsOff}</p>}
            {guildsOn && guilds.length === 0 && <p className={styles.hint}>{labels.guildsEmpty}</p>}
            {guilds.length > 0 && (
              <ul className={styles.lords}>
                {guilds.map((guild) => (
                  <li key={guild.id} className={styles.lord}>
                    <div className={styles.lordHead}>
                      <strong>
                        {guild.name} [{guild.tag}]
                      </strong>
                      <span className={styles.meta}>
                        {labels.guildLine(guild.lords.length, maxMembers, formatNumber(guild.points, language))}
                      </span>
                    </div>
                    <p className={styles.meta}>{guild.lords.map((lord) => lord.name).join(', ')}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
        {place.isError && <ErrorMessage>{errorMessage(t, place.error)}</ErrorMessage>}
        {bannersOn && !lordBannersOn && lords.data !== undefined && lords.data.length > 0 && (
          <p className={styles.hint}>{labels.bannersOff}</p>
        )}
        <ul className={styles.lords}>
          {lords.data?.map((lord: NpcLord) => {
            const choices = guildsOn ? guildChoices(lord, guilds, maxMembers) : [];
            return (
              <li key={lord.playerId} className={styles.lord}>
                <div className={styles.lordHead}>
                  <strong>{lord.name}</strong>
                  <span className={styles.lordSide}>
                    {labels.personalities[lord.personality]}
                    {choices.length > 0 && (
                      <MoreMenu
                        label={labels.more(lord.name)}
                        items={choices.map((choice) => ({
                          label: choiceLabel(choice),
                          icon: choiceIcons[choice.action],
                          disabled: place.isPending,
                          onSelect: () =>
                            place.mutate({ playerId: lord.playerId, action: choice.action, guildId: choice.guild?.id }),
                        }))}
                      />
                    )}
                  </span>
                </div>
                <p className={styles.meta}>
                  {labels.points(formatNumber(lord.points, language))} · {labels.villages(lord.villageCount)} ·{' '}
                  {labels.factor(formatDecimal(lord.productionFactor, language))}
                  {lord.braked && ` · ${labels.braked}`}
                  {lord.boosted && ` · ${labels.boosted}`}
                </p>
                {(guildsOn || lord.guildName != null) && (
                  <p className={styles.meta}>
                    {lord.guildName != null && lord.guildTag != null
                      ? labels.guild(lord.guildName, lord.guildTag)
                      : labels.noGuild}
                    {lord.guildByAdmin && ` · ${labels.placed}`}
                  </p>
                )}
                {showsBanners(lord) && (
                  <>
                    <p className={styles.meta}>
                      {labels.paladinLevel(lord.paladinLevel)} · {stash(lord)}
                    </p>
                    {lord.bannerVillages.map((village) => (
                      <p key={village.villageId} className={styles.meta}>
                        {village.name}:{' '}
                        {[village.banner, village.standard]
                          .filter((item): item is Equipped => item != null)
                          .map(equipped)
                          .join(' · ')}
                      </p>
                    ))}
                  </>
                )}
                {lord.learning != null && (
                  <>
                    {/* 1.6 UI review: the troop shifts first, the two lesson weights behind the (i) */}
                    <p className={`${styles.meta} ${styles.learnedLine}`}>
                      <span>
                        {lord.learning.changes.length > 0
                          ? labels.learned(mixChanges(lord.learning, (unit) => t.units[unit]))
                          : labels.learnedNoChange}
                      </span>
                      <LearningInfo learning={lord.learning} />
                    </p>
                    {lord.learning.walls.map((wall) => (
                      <p key={wall.villageId} className={styles.meta}>
                        {labels.wallGoal(wall.name, wall.level, formatDateTime(Date.parse(wall.until), language))}
                      </p>
                    ))}
                  </>
                )}
                <p className={styles.meta}>
                  {lord.nextThinkAt == null
                    ? labels.outOfTheGame
                    : labels.nextRound(formatDateTime(Date.parse(lord.nextThinkAt), language))}
                  {lord.lastThinkAt != null &&
                    ` · ${labels.lastRound(formatDateTime(Date.parse(lord.lastThinkAt), language))}`}
                </p>
              </li>
            );
          })}
        </ul>
      </div>
    </Card>
  );
}

/** What the two lesson weights of a learning lord mean (09-npc-lords §15.3), behind a small (i). */
function LearningInfo({ learning }: { learning: LordLearning }) {
  const { t } = useI18n();
  const labels = t.admin.lords;
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        className={styles.learnedInfo}
        aria-label={labels.learnedInfo}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <Icon name="info" />
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} title={labels.learnedInfo}>
        <div className={styles.learnedSheet}>
          <p>{labels.learnedAttacks(percentOf(learning.defenceWeight))}</p>
          <p>{labels.learnedWins(percentOf(learning.attackWeight))}</p>
          <p className={styles.meta}>{labels.learnedRest}</p>
        </div>
      </Sheet>
    </>
  );
}
