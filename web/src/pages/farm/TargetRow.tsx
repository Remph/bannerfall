import { useEffect, useId, useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import {
  troopsOfChoice,
  useChangeFarmUnits,
  useRemoveFarmTarget,
  useSendFarm,
  type FarmTarget,
} from '../../api/queries/farm';
import { useFarmTemplates } from '../../api/queries/farmTemplates';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import {
  bannerRollBackAt,
  farmSendEndsProtection,
  fitsHome,
  hasUnits,
  type SendAllNote,
  type SendAllSkipReason,
} from '../../game/farm';
import {
  formatCoords,
  formatDecimal,
  formatDuration,
  formatFactor,
  formatNumber,
  formatRecent,
  formatWhenExact,
} from '../../game/format';
import { unitsOfChoice } from '../../game/farmTemplates';
import { formatMorale } from '../../game/morale';
import { nightBonusAt } from '../../game/nightBonus';
import { serverNow } from '../../game/serverTime';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import { mapPath } from '../rally/rallyPaths';
import styles from './Farm.module.css';
import { TemplateLetterBadge } from './TemplateButtons';
import { TemplateChoice } from './TemplateChoice';

/** How long Send waits for the second tap (like deleting a report). */
const ARMED_MS = 4000;

interface TargetRowProps {
  villageId: number;
  target: FarmTarget;
  home: UnitMap;
  /** What the last "send all" did with this entry (M39); none if it didn't reach it. */
  note?: SendAllNote;
  /** A "send all" is running: the row's own Send waits for it. */
  sendingAll?: boolean;
}

/**
 * One target in two short lines (1.6, the farm list overhaul): the template letter, the village
 * and Send; under it the travel time, the loot (what the units can carry, and the last raid's haul
 * once one landed: "632/800", green when they carried all they could), small marks for what used
 * to take a line each (banner chance used, night bonus, morale, the target's beginner protection)
 * and when it was last sent; the line wraps rather than hide a mark. Tapping the row (a chevron says it opens) shows the units, the
 * facts in words, the full warnings and Edit / Remove in place, so the row keeps one button and a
 * list of 50 stays short. What matters right after a tap stays visible without opening: not enough
 * troops, the send's result, the reason "send all" skipped it.
 * While the sender's beginner protection lasts, an attack on a player's village ends it (02 §6, D9):
 * Send takes two taps – the first turns it into "Send anyway?", without a second it turns back
 * after a few seconds – and a line under the row says why. Barbarian villages stay one tap. The player's
 * own note on the village (1.4) stays one muted line under the facts: all barbarian villages share
 * one name.
 */
export function TargetRow({ villageId, target, home, note, sendingAll = false }: TargetRowProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const send = useSendFarm(villageId);
  const remove = useRemoveFarmTarget(villageId);
  const [editing, setEditing] = useState(false);
  const [open, setOpen] = useState(false);
  const detailsId = useId();
  const [armed, setArmed] = useState(false);
  // the note this row's own Send came after: it no longer says what happened last ("not enough
  // troops" next to a sent attack); the next "send all" brings new notes
  const [sentOverNote, setSentOverNote] = useState<SendAllNote>();
  const sentHere = send.variables === target.id;
  const template: UnitMap = target.units;
  const enough = fitsHome(template, home);
  const showNote = note !== undefined && note !== sentOverNote;
  // "Send all" sent this row: its last-sent fact gets a check instead of a line of its own (1.3)
  const sentByRun = showNote && note.kind === 'sent';
  // the run's "Not sent: not enough troops at home" says the same as the live line and more
  const shortNote = showNote && note.kind === 'skipped' && note.reason === 'NOT_ENOUGH_UNITS';
  const protectedUntil = target.protectedUntil == null ? undefined : Date.parse(target.protectedUntil);
  const village = target.target;
  const name = village.name ?? t.map.barbarianVillage;
  // serverNow(), not the ticking useGameNow(): up to 50 rows shouldn't re-render every second for a
  // protection that ends days later. A row rendered just before the end asks once too often at worst.
  const endsProtection = farmSendEndsProtection(Date.parse(me.protectedUntil), village.playerId, serverNow());
  // the night bonus (1.4) at the arrival if sent now, as on the rally point's confirmation
  const night = nightBonusAt(
    useWorld().data?.nightBonus,
    serverNow() + target.travelSeconds * 1000,
    village.playerId !== null,
  );
  // the same for the banner roll: the list is fetched again after every send
  const bannerRollBack = bannerRollBackAt(target.nextBannerRollAt, serverNow());

  useEffect(() => {
    if (!armed) {
      return;
    }
    const timer = setTimeout(() => setArmed(false), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armed]);

  function onSend() {
    if (endsProtection && !armed) {
      setArmed(true);
      return;
    }
    setArmed(false);
    setSentOverNote(note);
    send.mutate(target.id);
  }

  const lastSent = target.lastSentAt == null ? undefined : Date.parse(target.lastSentAt);
  const nightText = night === null ? undefined : t.rally.nightBonus(formatFactor(night, language));
  const moraleText = target.morale == null ? undefined : t.rally.morale(formatMorale(target.morale, language));
  return (
    <div className={styles.target} data-open={open || undefined}>
      <div className={styles.targetTop}>
        <button
          type="button"
          className={styles.targetMain}
          aria-expanded={open}
          aria-controls={detailsId}
          onClick={() => setOpen(!open)}
        >
          <TemplateLetterBadge
            letter={target.template}
            label={target.template === null ? t.farm.ownUnits : t.farm.templateLabel(target.template)}
          />
          <span className={styles.targetText}>
            <span className={styles.targetHead}>
              <span className={styles.targetName}>{name}</span>
              <span className={styles.coords}>{formatCoords(village.x, village.y)}</span>
              {/* phones: in the opened part only (an NPC lord's village name holds it anyway) */}
              {village.playerName != null && <span className={styles.owner}>{village.playerName}</span>}
            </span>
            <span className={styles.facts}>
              <span>
                <span className="sr-only">{t.farm.travelTime}: </span>
                {formatDuration(target.travelSeconds)}
              </span>
              <HaulFact capacity={target.haulCapacity} raid={target.lastRaid} />
              {bannerRollBack !== undefined && (
                <span className={styles.mark}>
                  <Icon name="BANNER_HALL" />
                  <span className="sr-only">{t.farm.bannerRollUsedShort}</span>
                </span>
              )}
              {night !== null && (
                <span className={`${styles.mark} ${styles.markNotice}`}>
                  <Icon name="moon" />
                  <span className="sr-only">{t.farm.nightShort}</span>×{formatFactor(night, language)}
                </span>
              )}
              {target.morale != null && (
                <span className={`${styles.mark} ${styles.markNotice}`}>
                  <span className="sr-only">{t.farm.moraleShort} </span>
                  {formatMorale(target.morale, language)}
                </span>
              )}
              {protectedUntil !== undefined && (
                <span className={`${styles.mark} ${styles.markWarn}`}>
                  <Icon name="shield" />
                  <span className="sr-only">{t.farm.protectedShort}</span>
                </span>
              )}
              {lastSent !== undefined && (
                <span className={sentByRun ? `${styles.fact} ${styles.lastSentDone}` : styles.fact}>
                  <Icon name={sentByRun ? 'check' : 'send'} />
                  <span className="sr-only">{t.farm.lastSentLabel}: </span>
                  {formatRecent(lastSent, serverNow(), language)}
                </span>
              )}
            </span>
            {target.note !== null && (
              // the player's own note on the village (1.4): written on the map, one line here
              <span className={styles.villageNote}>
                <Icon name="pencil" />
                <span className="sr-only">{t.map.note.label}: </span>
                <span className={styles.villageNoteText}>{target.note}</span>
              </span>
            )}
          </span>
          <span className={styles.chevron} aria-hidden="true">
            <Icon name="chevronDown" />
          </span>
        </button>
        <Button
          type="button"
          size="small"
          disabled={!enough || send.isPending || sendingAll}
          onClick={onSend}
          // a mouse or keyboard user who moves on doesn't leave it armed
          onBlur={() => setArmed(false)}
          // armed, the visible "Send anyway?" is the name (the line under the row says why)
          aria-label={armed ? undefined : t.farm.sendTo(name)}
        >
          {armed ? t.farm.sendAnyway : t.farm.send}
        </Button>
      </div>
      {(!enough && !shortNote) ||
      (armed && !open) ||
      (sentHere && (send.isSuccess || send.isError)) ||
      (showNote && note.kind === 'skipped') ? (
        <div className={styles.rowLines}>
          {/* "Send anyway?" says why without opening the row */}
          {armed && !open && <p className={styles.notice}>{t.rally.endsProtection}</p>}
          {!enough && !shortNote && <p className={styles.meta}>{t.farm.notEnough}</p>}
          {send.isSuccess && sentHere && (
            <p className={styles.sent} role="status">
              {t.farm.sent(formatWhenExact(Date.parse(send.data.arriveAt), serverNow(), language))}
            </p>
          )}
          {send.isError && sentHere && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
          {showNote && note.kind === 'skipped' && <SkippedLine reason={note.reason} />}
        </div>
      ) : null}
      <div id={detailsId} className={styles.details} hidden={!open}>
        {open && (
          <>
            <UnitSummary units={template} />
            <p className={styles.meta}>
              {village.playerName != null && <>{village.playerName} · </>}
              {t.farm.trip(formatDecimal(target.distance, language), formatDuration(target.travelSeconds))}
              {' · '}
              <Link className={styles.mapLink} to={mapPath(villageId, village)}>
                {t.farm.onMap}
              </Link>
            </p>
            {target.haulCapacity > 0 && (
              <p className={styles.meta}>{t.farm.capacityLine(formatNumber(target.haulCapacity, language))}</p>
            )}
            {target.lastRaid != null && (
              <p className={target.lastRaid.won ? styles.meta : styles.warn}>
                {t.farm.lastRaidLine(
                  formatWhenExact(Date.parse(target.lastRaid.at), serverNow(), language),
                  target.lastRaid.won ? formatNumber(target.lastRaid.loot, language) : undefined,
                  target.lastRaid.full,
                )}
              </p>
            )}
            {lastSent !== undefined && (
              <p className={styles.meta}>{t.farm.lastSent(formatWhenExact(lastSent, serverNow(), language))}</p>
            )}
            {bannerRollBack !== undefined && (
              <p className={styles.meta}>
                {t.farm.bannerRollUsed(formatWhenExact(bannerRollBack, serverNow(), language))}
              </p>
            )}
            {protectedUntil !== undefined && (
              <p className={styles.warn}>
                {t.farm.protectedUntil(formatWhenExact(protectedUntil, serverNow(), language))}
              </p>
            )}
            {endsProtection && <p className={styles.notice}>{t.rally.endsProtection}</p>}
            {nightText !== undefined && (
              <p className={`${styles.notice} ${styles.nightNotice}`}>
                <Icon name="moon" /> {nightText}
              </p>
            )}
            {/* morale (1.6) with today's points, as on the rally point's confirmation; only below 100 % */}
            {moraleText !== undefined && <p className={styles.notice}>{moraleText}</p>}
            <div className={styles.actions}>
              <Button type="button" variant="secondary" size="small" onClick={() => setEditing(true)}>
                <Icon name="pencil" /> {t.farm.edit}
              </Button>
              <Button
                type="button"
                variant="secondary"
                size="small"
                disabled={remove.isPending}
                onClick={() => remove.mutate(target.id)}
              >
                <Icon name="trash" /> {t.farm.remove}
              </Button>
            </div>
            {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
          </>
        )}
      </div>
      <EditSheet
        key={String(editing)}
        open={editing}
        villageId={villageId}
        target={target}
        home={home}
        onClose={() => setEditing(false)}
      />
    </div>
  );
}

/**
 * The loot fact of a row (1.6): "up to 800" before a raid landed, then the last haul over what the
 * units can carry now ("632/800"), the haul green when they carried all they could (the village had
 * more: send more), "lost" in the warning colour after a lost battle. Nothing for units that carry
 * nothing (scouts).
 */
function HaulFact({ capacity, raid }: { capacity: number; raid: FarmTarget['lastRaid'] }) {
  const { t, language } = useI18n();
  if (capacity === 0 && (raid == null || raid.won)) {
    return null;
  }
  return (
    <span className={styles.haul}>
      <Icon name="sack" />
      <span className="sr-only">{t.farm.lootLabel}: </span>
      {raid == null ? (
        t.farm.haulUpTo(formatNumber(capacity, language))
      ) : !raid.won ? (
        <span className={styles.haulLost}>{t.farm.raidLost}</span>
      ) : (
        <span>
          <span className={raid.full ? styles.haulFull : undefined}>{formatNumber(raid.loot, language)}</span>
          {`/${formatNumber(capacity, language)}`}
        </span>
      )}
    </span>
  );
}

/** The row's line when a "send all" skipped it: why (troops short in the warning colour). */
function SkippedLine({ reason }: { reason: SendAllSkipReason }) {
  const { t } = useI18n();
  return <p className={reason === 'NOT_ENOUGH_UNITS' ? styles.warn : styles.meta}>{t.farm.noteSkipped[reason]}</p>;
}

interface EditSheetProps {
  open: boolean;
  villageId: number;
  target: FarmTarget;
  home: UnitMap;
  onClose: () => void;
}

/** A new template for a target. `key` at the call makes it start from the saved one each time. */
function EditSheet({ open, villageId, target, home, onClose }: EditSheetProps) {
  const { t } = useI18n();
  const change = useChangeFarmUnits(villageId);
  const templates = useFarmTemplates().data ?? [];
  const [letter, setLetter] = useState(target.template);
  const [counts, setCounts] = useState<UnitMap>(target.units);
  const units = unitsOfChoice(letter, templates, counts);
  return (
    <Sheet open={open} onClose={onClose} title={t.farm.editTitle(target.target.name ?? t.map.barbarianVillage)}>
      <div className={styles.body}>
        <TemplateChoice
          templates={templates}
          value={letter}
          onChange={(next) => {
            setLetter(next);
            change.reset();
          }}
          counts={counts}
          home={home}
          onCounts={(type, count) => {
            setCounts((current) => ({ ...current, [type]: count }));
            change.reset();
          }}
        />
        {change.isError && <ErrorMessage>{errorMessage(t, change.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={!hasUnits(units) || change.isPending}
            onClick={() =>
              change.mutate({ targetId: target.id, troops: troopsOfChoice(letter, counts) }, { onSuccess: onClose })
            }
          >
            {t.farm.save}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
