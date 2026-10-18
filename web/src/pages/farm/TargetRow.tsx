import { useEffect, useState } from 'react';
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
import { MoreMenu } from '../../components/MoreMenu';
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
  formatRecent,
  formatWhen,
  formatWhenExact,
} from '../../game/format';
import { unitsOfChoice } from '../../game/farmTemplates';
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
 * One target: where, how far, the template, and Send (one attack) next to its name; edit and remove
 * sit behind "…" (1.3), so the row has one button and the list stays short.
 * While the sender's beginner protection lasts, an attack on a player's village ends it (02 §6, D9):
 * the row says so like the rally point's confirmation, and Send takes two taps – the first turns it
 * into "Send anyway?", without a second it turns back after a few seconds. Barbarian villages stay
 * one tap. A small tag says when the player's banner chance against the target is used for the day
 * (02 §21.7): the targets without it are the ones worth a try for a banner. The player's own note on
 * the village (1.4) is one muted line under the name: all barbarian villages share one name.
 */
export function TargetRow({ villageId, target, home, note, sendingAll = false }: TargetRowProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const send = useSendFarm(villageId);
  const remove = useRemoveFarmTarget(villageId);
  const [editing, setEditing] = useState(false);
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

  return (
    <div className={styles.target}>
      <div className={styles.targetTop}>
        <div className={styles.targetHead}>
          <span className={styles.targetName}>{name}</span>
          <Link className={styles.coords} to={mapPath(villageId, village)}>
            {formatCoords(village.x, village.y)}
          </Link>
          {village.playerName != null && <span className={styles.owner}>{village.playerName}</span>}
          {bannerRollBack !== undefined && (
            <span className={styles.bannerRoll}>
              <Icon name="BANNER_HALL" /> {t.farm.bannerRollUsed(formatWhen(bannerRollBack, serverNow(), language))}
            </span>
          )}
          {target.note !== null && (
            // the player's own note on the village (1.4): written on the map, one line here
            <span className={styles.villageNote}>
              <Icon name="pencil" />
              <span className="sr-only">{t.map.note.label}: </span>
              <span className={styles.villageNoteText}>{target.note}</span>
            </span>
          )}
        </div>
        <div className={styles.rowActions}>
          <Button
            type="button"
            size="small"
            disabled={!enough || send.isPending || sendingAll}
            onClick={onSend}
            // a mouse or keyboard user who moves on doesn't leave it armed
            onBlur={() => setArmed(false)}
            // armed, the visible "Send anyway?" is the name (the warning line below says why)
            aria-label={armed ? undefined : t.farm.sendTo(name)}
          >
            {armed ? t.farm.sendAnyway : t.farm.send}
          </Button>
          <MoreMenu
            label={t.farm.more(name)}
            items={[
              { label: t.farm.edit, icon: 'pencil', onSelect: () => setEditing(true) },
              {
                label: t.farm.remove,
                icon: 'trash',
                danger: true,
                disabled: remove.isPending,
                onSelect: () => remove.mutate(target.id),
              },
            ]}
          />
        </div>
      </div>
      <div className={styles.targetUnits}>
        <TemplateLetterBadge
          letter={target.template}
          label={target.template === null ? t.farm.ownUnits : t.farm.templateLabel(target.template)}
        />
        <UnitSummary units={template} />
      </div>
      <p className={styles.meta}>
        {t.farm.trip(formatDecimal(target.distance, language), formatDuration(target.travelSeconds))}
        {sentByRun ? (
          <>
            {' · '}
            <span className={styles.lastSentDone}>
              <Icon name="check" />
              {/* the list is fetched again before the run counts as done, so the time is there */}
              {target.lastSentAt != null
                ? t.farm.lastSent(formatRecent(Date.parse(target.lastSentAt), serverNow(), language))
                : t.farm.noteSent}
            </span>
          </>
        ) : (
          target.lastSentAt != null && (
            <> · {t.farm.lastSent(formatRecent(Date.parse(target.lastSentAt), serverNow(), language))}</>
          )
        )}
      </p>
      {protectedUntil !== undefined && (
        <p className={styles.warn}>{t.farm.protectedUntil(formatWhenExact(protectedUntil, serverNow(), language))}</p>
      )}
      {endsProtection && <p className={styles.notice}>{t.rally.endsProtection}</p>}
      {night !== null && (
        <p className={`${styles.notice} ${styles.nightNotice}`}>
          <Icon name="moon" /> {t.rally.nightBonus(formatFactor(night, language))}
        </p>
      )}
      {!enough && !shortNote && <p className={styles.meta}>{t.farm.notEnough}</p>}
      {send.isSuccess && sentHere && (
        <p className={styles.sent} role="status">
          {t.farm.sent(formatWhenExact(Date.parse(send.data.arriveAt), serverNow(), language))}
        </p>
      )}
      {send.isError && sentHere && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
      {showNote && note.kind === 'skipped' && <SkippedLine reason={note.reason} />}
      {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
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
