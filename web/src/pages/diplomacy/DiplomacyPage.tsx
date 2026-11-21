import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCancelOrder, useDiplomacy, useInviteLord, type Diplomacy, type Lord } from '../../api/queries/diplomacy';
import { useEnemies, useMyGuild } from '../../api/queries/guilds';
import { useVictory, type Victory, type VictorySide } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { inviteBlock, lordState, signedTrust } from '../../game/diplomacy';
import {
  formatCoords,
  formatDateTime,
  formatDateTimeExact,
  formatNumber,
  formatShare,
  formatWhen,
} from '../../game/format';
import { bracketTag } from '../../game/guilds';
import { orderLine } from '../../game/orders';
import { useServerNow } from '../../game/serverTime';
import { sideName, villageShare } from '../../game/victory';
import { useI18n } from '../../i18n';
import { useWorldStill } from '../../layout/worldEnd';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { OWN_GUILD_PATH, guildPath } from '../guild/guildPaths';
import { playerPath } from '../ranking/rankingPaths';
import styles from './DiplomacyPage.module.css';
import { OrderSheet } from './OrderSheet';

/**
 * /diplomacy (2.1 S2b, 12-solo-and-allies §7): the court of the lords and the race against it, the
 * allies, and every other lord with its trust in the player. Only in a world with lord guilds.
 * One action per row at most: the quiet "Invite" of a lord the player can ask now. Outside a solo
 * world no lord can become an ally: the page lists the lords and their guilds only, without the
 * court, trust or invitations (2.1 feature review, finding 03).
 */
export function DiplomacyPage() {
  const { t } = useI18n();
  const page = useDiplomacy();
  const victory = useVictory();
  const labels = t.diplomacy;

  if (page.isError) {
    return (
      <div className={styles.page}>
        <h1>{labels.title}</h1>
        <ErrorMessage>{errorMessage(t, page.error)}</ErrorMessage>
        <ActionsStayOn>
          <div>
            <Button type="button" onClick={() => void page.refetch()}>
              {t.common.retry}
            </Button>
          </div>
        </ActionsStayOn>
      </div>
    );
  }
  if (page.data === undefined) {
    return <p>{t.common.loading}</p>;
  }
  const data = page.data;
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <h1>{labels.title}</h1>
        <p className={styles.hint}>{data.soloMode ? labels.explainer : labels.explainerWorld}</p>
      </header>
      {data.soloMode && <CourtCard data={data} victory={victory.data} />}
      {data.soloMode && <AlliesCard data={data} />}
      {data.soloMode && data.allies.length > 0 && <MarkedCard />}
      <LordsCard data={data} />
      {data.soloMode && <p className={styles.howTrust}>{labels.howTrust}</p>}
    </div>
  );
}

/** The court's name, its guild, its goal, and the race between it and the player's side. */
function CourtCard({ data, victory }: { data: Diplomacy; victory: Victory | undefined }) {
  const { t, language } = useI18n();
  const labels = t.diplomacy;
  const court = data.court;
  if (court === null || court === undefined) {
    return (
      <Card title={labels.courtHeading}>
        <p className={styles.empty}>{labels.noCourt}</p>
      </Card>
    );
  }
  const name = sideName(court, t);
  const members = [...data.allies, ...data.lords].filter((lord) => lord.guild?.id === court.id).length;
  const guildLine = labels.courtGuild(`${court.tag === null ? '' : `${bracketTag(court.tag)} `}${court.name}`, members);
  return (
    // the court's name is the card's title: one headline, not "The court" over "The Raven Court"
    <Card title={name}>
      <div className={styles.court}>
        <p className={styles.sub}>
          <Link to={guildPath(court.id)} className={styles.inlineLink}>
            {guildLine}
          </Link>
        </p>
        {victory !== undefined && <p className={styles.goal}>{goalLine(t, victory, name, language)}</p>}
        {victory !== undefined && (
          <Race victory={victory} court={court} mine={data.mine ?? null} allVillages={data.allVillages} />
        )}
      </div>
    </Card>
  );
}

type Texts = ReturnType<typeof useI18n>['t'];

type Language = ReturnType<typeof useI18n>['language'];

/** What the court is after, by how the world is won. */
function goalLine(t: Texts, victory: Victory, court: string, language: Language): string | null {
  const labels = t.diplomacy;
  if (victory.mode === 'DOMINATION') {
    return labels.goalDomination(court, victory.dominationPercent);
  }
  if (victory.mode === 'KEEPS') {
    return labels.goalKeeps(court, victory.keepsNeeded, victory.keepCount);
  }
  return victory.endsAt === null
    ? null
    : labels.goalEndDate(court, formatDateTime(Date.parse(victory.endsAt), language));
}

/** The race's measure of a side: its share of the land, its keeps, or its points. */
function measureOf(victory: Victory, side: VictorySide | null, allVillages: number): number {
  if (side === null) {
    return 0;
  }
  if (victory.mode === 'DOMINATION') {
    return villageShare(side, allVillages);
  }
  return victory.mode === 'KEEPS' ? side.keeps : side.points;
}

/** Where a bar is full: the share or keeps that win, or in a points race the bigger of the two. */
function targetOf(victory: Victory, values: readonly number[]): number {
  if (victory.mode === 'DOMINATION') {
    return victory.dominationPercent;
  }
  return victory.mode === 'KEEPS' ? victory.keepsNeeded : Math.max(...values, 1);
}

function figureOf(t: Texts, victory: Victory, value: number, language: Language): string {
  const labels = t.diplomacy;
  if (victory.mode === 'DOMINATION') {
    return labels.share(formatShare(value, language), victory.dominationPercent);
  }
  return victory.mode === 'KEEPS'
    ? labels.keeps(value, victory.keepsNeeded)
    : labels.points(formatNumber(value, language));
}

const FULL_PERCENT = 100;

/** Two bars, the court's and the player's (or his guild's), measured like the world is won. */
function Race({
  victory,
  court,
  mine,
  allVillages,
}: {
  victory: Victory;
  court: VictorySide;
  mine: VictorySide | null;
  allVillages: number;
}) {
  const { t, language } = useI18n();
  const labels = t.diplomacy;
  const courtValue = measureOf(victory, court, allVillages);
  const mineValue = measureOf(victory, mine, allVillages);
  const target = targetOf(victory, [courtValue, mineValue]);
  const figure = (value: number) => figureOf(t, victory, value, language);
  const rows: { key: string; name: string; value: number; mine: boolean }[] = [
    { key: 'court', name: sideName(court, t), value: courtValue, mine: false },
    {
      key: 'mine',
      name: mine === null || mine.kind === 'PLAYER' ? labels.you : labels.yourGuild,
      value: mineValue,
      mine: true,
    },
  ];
  const lead = rows.find(
    (row) => row.value > Math.max(...rows.filter((other) => other !== row).map((o) => o.value)),
  )?.key;
  return (
    <div className={styles.race}>
      <h3 className={styles.raceTitle}>{labels.race}</h3>
      <ul className={styles.raceList}>
        {rows.map((row) => (
          <li key={row.key} className={styles.raceRow}>
            <span className={styles.raceName}>
              {row.name}
              {lead === row.key && <span className={styles.note}>{labels.ahead}</span>}
            </span>
            <span className={styles.raceFigure}>{figure(row.value)}</span>
            <span className={styles.track} aria-hidden="true">
              <span
                className={row.mine ? `${styles.fill} ${styles.fillMine}` : styles.fill}
                style={{ width: `${Math.min(FULL_PERCENT, (row.value * FULL_PERCENT) / target)}%` }}
              />
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** The allies, each with the order it carries (2.1 S3) and the one action on it: take it back. */
function AlliesCard({ data }: { data: Diplomacy }) {
  const { t } = useI18n();
  const labels = t.diplomacy;
  const cancel = useCancelOrder();
  return (
    <Card title={labels.allies}>
      {data.allies.length === 0 ? (
        <p className={styles.empty}>{labels.alliesEmpty}</p>
      ) : (
        <ul className={styles.list}>
          {data.allies.map((lord) => (
            <LordRow
              key={lord.playerId}
              lord={lord}
              cancelling={cancel.isPending && cancel.variables === lord.playerId}
              onCancel={() => cancel.mutate(lord.playerId)}
            />
          ))}
        </ul>
      )}
      {cancel.isError && <ErrorMessage>{errorMessage(t, cancel.error)}</ErrorMessage>}
    </Card>
  );
}

/**
 * The guild's marked enemy villages (1.7) as the first targets for orders (2.1 S3, 12 §8): each opens
 * the order sheet. Any other village gets its orders from the map's panel.
 */
function MarkedCard() {
  const { t } = useI18n();
  const labels = t.diplomacy.orders;
  const villages = useEnemies().data?.villages ?? [];
  const [villageId, setVillageId] = useState<number | null>(null);
  // a paused or ended world takes no orders (UI review 2.1, finding 04)
  const still = useWorldStill();
  return (
    <Card title={labels.marked}>
      <p className={styles.blockedHint}>{villages.length === 0 ? labels.markedEmpty : labels.markedHint}</p>
      {villages.length > 0 && (
        <ul className={styles.list}>
          {villages.map((village) => {
            const name = `${village.name} ${formatCoords(village.x, village.y)}`;
            return (
              <li key={village.id} className={styles.row}>
                <span className={styles.rowMain}>
                  <span className={styles.rowTitle}>{name}</span>
                  <span className={styles.sub}>{village.ownerName}</span>
                </span>
                {!still && (
                  <Button
                    type="button"
                    variant="secondary"
                    size="small"
                    aria-label={labels.orderFor(name)}
                    onClick={() => setVillageId(village.id)}
                  >
                    {labels.order}
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <OrderSheet villageId={villageId} onClose={() => setVillageId(null)} />
    </Card>
  );
}

/** The lords outside the player's guild, with the one thing the player can do about them. */
function LordsCard({ data }: { data: Diplomacy }) {
  const { t } = useI18n();
  const labels = t.diplomacy;
  const inGuild = useMyGuild().data?.guild != null;
  const invite = useInviteLord();
  // why nobody can be invited now, once for the whole list: one cause, one line
  const cause = inviteBlock(data, inGuild);
  const blocked = cause === null ? null : labels[cause];
  // outside a solo world the page's own explainer says why nobody joins
  const blockedLine = data.soloMode ? blocked : null;
  return (
    <Card title={data.soloMode ? labels.lords : labels.lordsWorld}>
      {blockedLine !== null && (
        <p className={styles.blocked}>
          {blockedLine}
          {data.soloMode && !inGuild && (
            <>
              {' '}
              <Link to={OWN_GUILD_PATH} className={styles.inlineLink}>
                {labels.toGuild}
              </Link>
            </>
          )}
        </p>
      )}
      {data.lords.length === 0 ? (
        <p className={styles.empty}>{labels.lordsEmpty}</p>
      ) : (
        <ul className={styles.list}>
          {data.lords.map((lord) => (
            <LordRow
              key={lord.playerId}
              lord={lord}
              canInvite={blocked === null}
              inviting={invite.isPending && invite.variables === lord.playerId}
              onInvite={() => invite.mutate(lord.playerId)}
            />
          ))}
        </ul>
      )}
      {invite.isError && <ErrorMessage>{errorMessage(t, invite.error)}</ErrorMessage>}
    </Card>
  );
}

interface LordRowProps {
  lord: Lord;
  /** The player may invite lords now (solo world, leader of a guild with room); missing for allies. */
  canInvite?: boolean;
  inviting?: boolean;
  onInvite?: () => void;
  /** An ally's order is being taken back. */
  cancelling?: boolean;
  /** Takes an ally's order back (2.1 S3); missing for the other lords. */
  onCancel?: () => void;
}

/**
 * One lord: name and guild, its last change of trust, and the trust itself as the row's biggest
 * figure. Below, for a lord outside the guild: "joins at", and the invite or why it waits. Outside
 * a solo world (no trust): name, guild and points only.
 */
function LordRow({ lord, canInvite = false, inviting = false, onInvite, cancelling = false, onCancel }: LordRowProps) {
  const { t, language } = useI18n();
  const labels = t.diplomacy;
  const now = useServerNow();
  const last = lord.changes[0];
  const state = lordState(lord, now);
  const status =
    state.kind === 'invited'
      ? labels.invited
      : state.kind === 'waiting'
        ? labels.askAgain(formatWhen(state.until, now, language))
        : null;
  // a paused or ended world takes no invite and no order back (UI review 2.1, finding 04)
  const still = useWorldStill();
  const showInvite = !still && canInvite && state.kind === 'open';
  // an ally's order (2.1 S3): what, and where it stands; "Take back" while it isn't refused
  const order = lord.ally ? (lord.order ?? null) : null;
  const ordered = order === null ? null : orderLine(t, order, (ms) => formatDateTimeExact(ms, language));
  const showCancel = !still && order !== null && order.state !== 'REFUSED' && onCancel !== undefined;
  return (
    <li className={styles.row}>
      <span className={styles.rowMain}>
        <Link to={playerPath(lord.playerId)} className={styles.rowName}>
          {lord.guild !== null && lord.guild !== undefined && (
            <span className={styles.tag}>{bracketTag(lord.guild.tag)}</span>
          )}
          {lord.name}
        </Link>
        <span className={styles.sub}>
          {lord.trust === null
            ? labels.points(formatNumber(lord.points, language))
            : last === undefined
              ? labels.noChange
              : labels.change(last.delta, labels.reasons[last.reason](last.village ?? '—'))}
        </span>
      </span>
      {lord.trust !== null && (
        <span className={styles.trust}>
          <span className={styles.trustValue} aria-label={labels.trustLabel(lord.trust)}>
            {signedTrust(lord.trust)}
          </span>
          {!lord.ally && lord.bar !== null && <span className={styles.bar}>{labels.joinsAt(lord.bar)}</span>}
          {/* the server sends the bar only after the first contact (09 §16.7, security review 2.1) */}
          {!lord.ally && lord.bar === null && lord.variant === null && (
            <span className={styles.notMet}>{labels.notMet}</span>
          )}
        </span>
      )}
      {(status !== null || showInvite || ordered !== null) && (
        <span className={styles.rowFoot}>
          {status !== null && <span className={styles.status}>{status}</span>}
          {ordered !== null && <span className={styles.status}>{ordered}</span>}
          {showCancel && (
            <Button
              type="button"
              variant="secondary"
              size="small"
              aria-label={t.diplomacy.orders.takeBackFor(lord.name)}
              disabled={cancelling}
              onClick={onCancel}
            >
              {t.diplomacy.orders.takeBack}
            </Button>
          )}
          {showInvite && (
            <Button
              type="button"
              variant="secondary"
              size="small"
              aria-label={labels.inviteFor(lord.name)}
              disabled={inviting}
              onClick={onInvite}
            >
              {labels.invite}
            </Button>
          )}
        </span>
      )}
    </li>
  );
}
