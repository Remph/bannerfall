import { useId, useState, type ReactNode, type Ref } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useNow } from '../../api/queries/now';
import { useCancelMovement, useSetReturnPush, type Movement } from '../../api/queries/troops';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Countdown } from '../../components/Countdown';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon, type IconName } from '../../components/Icon';
import { SegmentedControl } from '../../components/SegmentedControl';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import { formatFactor, formatNumber, formatWhen, formatWhenExact } from '../../game/format';
import { nowSections, orderProgress, type NowRow, type NowScope } from '../../game/now';
import { nightBonusAt } from '../../game/nightBonus';
import { unitsDone } from '../../game/recruitment';
import { useGameNow } from '../../game/serverTime';
import { canCancel, travelProgress, type MovementKind } from '../../game/troops';
import { useI18n, useT } from '../../i18n';
import { useOffersReturnPush } from '../../push/returnChoice';
import { CancelConfirm, ReturnBell } from '../../pages/rally/MovementCards';
import { mapPath } from '../../pages/rally/rallyPaths';
import { villageLabel } from '../../pages/rally/villageLabel';
import { useWorldEnded } from '../worldEnd';
import styles from './NowList.module.css';
import { loadNowScope, saveNowScope } from './nowScope';

const movementIcons: Record<MovementKind, IconName> = { ATTACK: 'troops', SUPPORT: 'shield', RETURN: 'returning' };

interface NowListProps {
  /** The village the player looks at: the "this village" scope, and the base of the map links. */
  currentVillageId: number | undefined;
  /** Called when a link inside the list leaves the page (an overlay closes). */
  onNavigate?: () => void;
  /** The heading, for a place that moves the keyboard focus to it when it opens. */
  headingRef?: Ref<HTMLHeadingElement>;
  /** A button next to the heading (the drawer's and the sheet's close button). */
  headingAction?: ReactNode;
  /** The id of the heading, so the dialog around the list can be named by it. */
  headingId?: string;
}

/**
 * Everything that runs in the player's villages (2.0): attacks on the way in, the build queue, the
 * units in training (and being dismissed, and researched), and commands, supports and merchants on
 * the way. The same list sits in the column from 1600 px, the drawer on the map and on narrower
 * desktops, and the phone's pull-down sheet. Attacks show for every village whatever the switch says.
 */
export function NowList({ currentVillageId, onNavigate, headingRef, headingAction, headingId }: NowListProps) {
  const { t, language } = useI18n();
  // the place around the list (column, drawer, sheet) keeps it fresh, also while the list is closed
  const now = useNow();
  const [scope, setScope] = useState<NowScope>(loadNowScope);
  const nowMs = useGameNow();
  const me = useCurrentAccount();
  const ended = useWorldEnded();
  const protectedUntil = Date.parse(me.protectedUntil);
  const protectedNow = !ended && protectedUntil > nowMs;

  const choose = (next: NowScope) => {
    setScope(next);
    saveNowScope(next);
  };
  const villages = now.data?.villages ?? [];
  const sections = now.data === undefined ? undefined : nowSections(now.data, scope, currentVillageId);
  const showScope = villages.length > 1;
  const tagged = scope === 'all' && villages.length > 1;

  return (
    <div className={styles.list}>
      <div className={styles.head}>
        <h2 id={headingId} ref={headingRef} tabIndex={-1} className={styles.title}>
          {t.now.title}
        </h2>
        {headingAction}
      </div>
      {showScope && (
        <div className={styles.scope}>
          <SegmentedControl
            label={t.now.scopeLabel}
            options={[
              { value: 'this', label: t.now.scopeThis },
              { value: 'all', label: t.now.scopeAll },
            ]}
            value={scope}
            onChange={choose}
            compact
          />
        </div>
      )}
      {protectedNow && (
        <ul className={styles.rows}>
          <li className={styles.row}>
            <span className={styles.icon}>
              <Icon name="shield" />
            </span>
            <span className={styles.text}>
              <b>{t.now.protection}</b>
              <span>{t.now.protectionEnds(formatWhenExact(protectedUntil, nowMs, language))}</span>
            </span>
            <Countdown until={me.protectedUntil} className={styles.time} />
          </li>
        </ul>
      )}
      {now.isError && sections === undefined && (
        <div className={styles.error}>
          <ErrorMessage>{errorMessage(t, now.error)}</ErrorMessage>
        </div>
      )}
      {now.isPending && <p className={styles.empty}>{t.common.loading}</p>}
      {sections !== undefined && sections.count === 0 && !protectedNow && (
        <p className={styles.empty}>{scope === 'this' && showScope ? t.now.emptyHere : t.now.empty}</p>
      )}
      {sections !== undefined && (
        <>
          <Section
            title={t.now.sections.attacks}
            war
            rows={sections.attacks}
            // attacks show whatever the switch says: under "this village" one on another village still names it
            tagged={tagged}
            tagOthers={villages.length > 1}
            {...{ currentVillageId, onNavigate }}
          />
          <Section
            title={t.now.sections.building}
            rows={sections.building}
            tagged={tagged}
            {...{ currentVillageId, onNavigate }}
          />
          <Section
            title={t.now.sections.training}
            rows={sections.training}
            tagged={tagged}
            {...{ currentVillageId, onNavigate }}
          />
          <Section
            title={t.now.sections.way}
            rows={sections.way}
            tagged={tagged}
            {...{ currentVillageId, onNavigate }}
          />
        </>
      )}
    </div>
  );
}

interface SectionProps {
  title: string;
  rows: NowRow[];
  war?: boolean;
  /** Every row names its village (the list covers several). */
  tagged: boolean;
  /** Only the rows of another village than the current one name it. */
  tagOthers?: boolean;
  currentVillageId: number | undefined;
  onNavigate?: () => void;
}

function Section({ title, rows, war = false, tagged, tagOthers = false, currentVillageId, onNavigate }: SectionProps) {
  const headingId = useId();
  if (rows.length === 0) {
    return null;
  }
  return (
    <section aria-labelledby={headingId} className={styles.section}>
      <h3 id={headingId} className={styles.sectionTitle}>
        {title}
      </h3>
      <ul className={styles.rows}>
        {rows.map((row) => (
          <RowItem
            key={row.key}
            row={row}
            war={war}
            tagged={tagged || (tagOthers && row.village.id !== currentVillageId)}
            currentVillageId={currentVillageId}
            onNavigate={onNavigate}
          />
        ))}
      </ul>
    </section>
  );
}

interface LineProps {
  icon: IconName;
  title: ReactNode;
  sub?: ReactNode;
  /** The village's name before the sub line, when the list covers several villages. */
  tag?: string;
  time?: ReactNode;
  /** 0 to 1: the bar under the line. */
  bar?: number;
  war?: boolean;
  children?: ReactNode;
  actions?: ReactNode;
}

function Line({ icon, title, sub, tag, time, bar, war = false, children, actions }: LineProps) {
  return (
    <li className={war ? `${styles.row} ${styles.war}` : styles.row}>
      <span className={styles.icon}>
        <Icon name={icon} />
      </span>
      <span className={styles.text}>
        <b>{title}</b>
        {(sub !== undefined || tag !== undefined) && (
          <span>
            {tag !== undefined && <em className={styles.tag}>{tag}</em>}
            {sub}
          </span>
        )}
      </span>
      {time !== undefined && <span className={styles.time}>{time}</span>}
      {bar !== undefined && (
        <span className={styles.bar} aria-hidden="true">
          <span className={styles.fill} style={{ transform: `scaleX(${bar})` }} />
        </span>
      )}
      {children !== undefined && <span className={styles.extra}>{children}</span>}
      {actions !== undefined && <span className={styles.actions}>{actions}</span>}
    </li>
  );
}

function RowItem({
  row,
  war,
  tagged,
  currentVillageId,
  onNavigate,
}: {
  row: NowRow;
  war: boolean;
  tagged: boolean;
  currentVillageId: number | undefined;
  onNavigate?: () => void;
}) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const night = useWorld().data?.nightBonus;
  const tag = tagged ? (row.village.name ?? undefined) : undefined;

  switch (row.kind) {
    case 'build': {
      const order = row.order;
      const started = Date.parse(order.startAt) <= now;
      return (
        <Line
          icon={order.building}
          tag={tag}
          title={
            <>
              {t.buildings[order.building]} {t.village.level(order.targetLevel)}
              {order.demolish && <> · {t.buildQueue.demolition}</>}
            </>
          }
          sub={started ? t.buildQueue.readyAt(formatWhen(Date.parse(order.endAt), now, language)) : t.now.waits}
          time={started ? <Countdown until={order.endAt} /> : undefined}
          bar={started ? orderProgress(order, now) : undefined}
        />
      );
    }
    case 'train': {
      const order = row.order;
      const done = unitsDone(order, now);
      return (
        <Line
          icon={order.unit}
          tag={tag}
          title={t.recruitQueue.order(formatNumber(order.count, language), t.units[order.unit])}
          sub={
            <>
              {t.buildings[order.building]} ·{' '}
              {t.recruitQueue.done(formatNumber(done, language), formatNumber(order.count, language))}
              {row.waiting > 0 && <> · {t.now.moreWaiting(row.waiting)}</>}
            </>
          }
          time={<Countdown until={order.endAt} />}
          bar={orderProgress(order, now)}
        />
      );
    }
    case 'dismiss': {
      const order = row.order;
      const done = unitsDone(order, now);
      return (
        <Line
          icon={order.unit}
          tag={tag}
          title={t.now.dismissing(formatNumber(order.count, language), t.units[order.unit])}
          sub={
            <>
              {t.buildings[order.building]} ·{' '}
              {t.recruitQueue.done(formatNumber(done, language), formatNumber(order.count, language))}
              {row.waiting > 0 && <> · {t.now.moreWaiting(row.waiting)}</>}
            </>
          }
          time={<Countdown until={order.endAt} />}
          bar={orderProgress(order, now)}
        />
      );
    }
    case 'research': {
      const order = row.order;
      return (
        <Line
          icon={order.unit}
          tag={tag}
          title={t.now.researching(t.units[order.unit], order.level)}
          sub={t.buildings.SMITHY}
          time={<Countdown until={order.endAt} />}
          bar={orderProgress(order, now)}
        />
      );
    }
    case 'attack': {
      const movement = row.movement;
      const hit = nightBonusAt(night, Date.parse(movement.arriveAt), true);
      return (
        <Line
          icon="troops"
          war={war}
          tag={tag}
          title={`${t.rally.incoming.ATTACK} ${villageLabel(t, movement.sourceVillage)}`}
          sub={
            <>
              {movement.sourcePlayerName !== null && <>{movement.sourcePlayerName} · </>}
              {t.rally.arrivesAt(formatWhenExact(Date.parse(movement.arriveAt), now, language))}
              {hit !== null && (
                <>
                  {' · '}
                  <Icon name="moon" /> {t.rally.nightTag(formatFactor(hit, language))}
                </>
              )}
              {' · '}
              <Link
                className={styles.onMap}
                to={mapPath(currentVillageId ?? row.village.id, movement.sourceVillage)}
                onClick={onNavigate}
              >
                {t.rally.onMap} <span className="sr-only">({villageLabel(t, movement.sourceVillage)})</span>
                <span aria-hidden="true">›</span>
              </Link>
            </>
          }
          time={<Countdown until={movement.arriveAt} />}
        />
      );
    }
    case 'incoming': {
      const movement = row.movement;
      return (
        <Line
          icon={movementIcons[movement.kind]}
          tag={tag}
          title={`${t.rally.incoming[movement.kind]} ${villageLabel(t, movement.sourceVillage)}`}
          sub={t.rally.arrivesAt(formatWhenExact(Date.parse(movement.arriveAt), now, language))}
          time={<Countdown until={movement.arriveAt} />}
          bar={
            movement.departAt === null
              ? undefined
              : travelProgress({ departAt: movement.departAt, arriveAt: movement.arriveAt }, now)
          }
        >
          {movement.units !== null && <UnitSummary units={movement.units} />}
        </Line>
      );
    }
    case 'outgoing': {
      const movement = row.movement;
      return (
        <Line
          icon={movementIcons[movement.kind]}
          tag={tag}
          title={`${t.rally.outgoing[movement.kind]} ${villageLabel(t, movement.targetVillage)}`}
          sub={t.rally.arrivesAt(formatWhenExact(Date.parse(movement.arriveAt), now, language))}
          time={<Countdown until={movement.arriveAt} />}
          bar={travelProgress(movement, now)}
          actions={<OutgoingActions movement={movement} />}
        >
          <UnitSummary units={movement.units} />
        </Line>
      );
    }
    case 'merchants': {
      const transport = row.transport;
      const until = transport.arrived ? transport.returnAt : transport.arriveAt;
      return (
        <Line
          icon="MARKET"
          tag={tag}
          title={
            transport.arrived
              ? t.now.merchantsHome(villageLabel(t, transport.targetVillage))
              : t.now.merchantsTo(villageLabel(t, transport.targetVillage))
          }
          sub={`${t.now.merchantsCount(transport.merchants)} · ${t.rally.arrivesAt(formatWhenExact(Date.parse(until), now, language))}`}
          time={<Countdown until={until} />}
          bar={travelProgress(
            { departAt: transport.arrived ? transport.arriveAt : transport.departAt, arriveAt: until },
            now,
          )}
        />
      );
    }
  }
}

/**
 * The bell and the cancel button of an own command, like the rally point's Outgoing card: cancel
 * asks first, then the troops turn around; the button shows how long it can still be cancelled.
 */
function OutgoingActions({ movement }: { movement: Movement }) {
  const t = useT();
  const now = useGameNow();
  const cancel = useCancelMovement(movement.homeVillage.id);
  const returnPush = useSetReturnPush(movement.homeVillage.id);
  const offersReturnPush = useOffersReturnPush();
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const cancellable = movement.cancellableUntil !== null && canCancel(movement, now);
  const bell = movement.kind === 'ATTACK' && offersReturnPush;
  if (!bell && !cancellable && error === null) {
    return null;
  }
  return (
    <>
      {bell && (
        <ReturnBell
          movement={movement}
          pending={returnPush.isPending}
          onChange={(on) => {
            setError(null);
            returnPush.mutate({ movementId: movement.id, on }, { onError: setError });
          }}
        />
      )}
      {/* disappears on the second the window closes (cancellableUntil is exclusive) */}
      {cancellable && movement.cancellableUntil !== null && (
        <Button
          type="button"
          variant="danger"
          size="small"
          disabled={cancel.isPending}
          onClick={() => setConfirming(true)}
        >
          {t.rally.cancel}
          <span className="sr-only">
            {' '}
            ({t.rally.outgoing[movement.kind]} {villageLabel(t, movement.targetVillage)})
          </span>{' '}
          <Countdown until={movement.cancellableUntil} />
        </Button>
      )}
      {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
      {/* closes by itself when the window ends meanwhile (the server would answer CANCEL_WINDOW_OVER) */}
      <Sheet open={confirming && cancellable} onClose={() => setConfirming(false)} title={t.rally.cancelTitle}>
        <CancelConfirm
          movement={movement}
          pending={cancel.isPending}
          onKeep={() => setConfirming(false)}
          onConfirm={() =>
            cancel.mutate(movement.id, {
              onSuccess: () => setConfirming(false),
              onError: (failure) => {
                setConfirming(false);
                setError(failure);
              },
            })
          }
        />
      </Sheet>
    </>
  );
}
