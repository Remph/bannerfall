import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useChangeFarmUnits, useRemoveFarmTarget, useSendFarm, type FarmTarget } from '../../api/queries/farm';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import { farmSendEndsProtection, fitsHome, hasUnits, templateOf, type SendAllNote } from '../../game/farm';
import { formatCoords, formatDecimal, formatDuration, formatRecent, formatWhenExact } from '../../game/format';
import { serverNow } from '../../game/serverTime';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import { mapPath } from '../rally/rallyPaths';
import styles from './Farm.module.css';
import { TemplatePicker } from './TemplatePicker';

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
 * One target: where, how far, the template, and the buttons: send (one attack), edit, remove.
 * While the sender's beginner protection lasts, an attack on a player's village ends it (02 §6, D9):
 * the row says so like the rally point's confirmation, and Send takes two taps – the first turns it
 * into "Send anyway?", without a second it turns back after a few seconds. Barbarian villages stay
 * one tap.
 */
export function TargetRow({ villageId, target, home, note, sendingAll = false }: TargetRowProps) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const send = useSendFarm(villageId);
  const remove = useRemoveFarmTarget(villageId);
  const [editing, setEditing] = useState(false);
  const [armed, setArmed] = useState(false);
  // the note this row's own Send came after: it no longer says what happened last ("Stopped here"
  // next to a sent attack); the next "send all" brings new notes
  const [sentOverNote, setSentOverNote] = useState<SendAllNote>();
  const sentHere = send.variables === target.id;
  const template: UnitMap = target.units;
  const enough = fitsHome(template, home);
  const protectedUntil = target.protectedUntil == null ? undefined : Date.parse(target.protectedUntil);
  const village = target.target;
  // serverNow(), not the ticking useServerNow(): up to 50 rows shouldn't re-render every second for a
  // protection that ends days later. A row rendered just before the end asks once too often at worst.
  const endsProtection = farmSendEndsProtection(Date.parse(me.protectedUntil), village.playerId, serverNow());

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
      <div className={styles.targetHead}>
        <span className={styles.targetName}>{village.name ?? t.map.barbarianVillage}</span>
        <Link className={styles.coords} to={mapPath(villageId, village)}>
          {formatCoords(village.x, village.y)}
        </Link>
        {village.playerName != null && <span className={styles.owner}>{village.playerName}</span>}
      </div>
      <UnitSummary units={template} />
      <p className={styles.meta}>
        {t.farm.trip(formatDecimal(target.distance, language), formatDuration(target.travelSeconds))}
        {target.lastSentAt != null && (
          <> · {t.farm.lastSent(formatRecent(Date.parse(target.lastSentAt), serverNow(), language))}</>
        )}
      </p>
      {protectedUntil !== undefined && (
        <p className={styles.warn}>{t.farm.protectedUntil(formatWhenExact(protectedUntil, serverNow(), language))}</p>
      )}
      {endsProtection && <p className={styles.notice}>{t.rally.endsProtection}</p>}
      {!enough && <p className={styles.meta}>{t.farm.notEnough}</p>}
      {send.isSuccess && sentHere && (
        <p className={styles.sent} role="status">
          {t.farm.sent(formatWhenExact(Date.parse(send.data.arriveAt), serverNow(), language))}
        </p>
      )}
      {send.isError && sentHere && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
      {note !== undefined && note !== sentOverNote && <SendAllNoteLine note={note} />}
      {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
      <div className={styles.actions}>
        <Button
          type="button"
          disabled={!enough || send.isPending || sendingAll}
          onClick={onSend}
          // a mouse or keyboard user who moves on doesn't leave it armed
          onBlur={() => setArmed(false)}
          // armed, the visible "Send anyway?" is the name (the warning line above says why)
          aria-label={armed ? undefined : t.farm.sendTo(village.name ?? t.map.barbarianVillage)}
        >
          {armed ? t.farm.sendAnyway : t.farm.send}
        </Button>
        <Button type="button" variant="secondary" onClick={() => setEditing(true)}>
          {t.farm.edit}
        </Button>
        <Button type="button" variant="secondary" disabled={remove.isPending} onClick={() => remove.mutate(target.id)}>
          {t.farm.remove}
        </Button>
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

/** The row's line after a "send all": sent, skipped and why, or where the run stopped. */
function SendAllNoteLine({ note }: { note: SendAllNote }) {
  const { t } = useI18n();
  switch (note.kind) {
    case 'sent':
      return <p className={styles.sent}>{t.farm.noteSent}</p>;
    case 'skipped':
      return <p className={styles.meta}>{t.farm.noteSkipped[note.reason]}</p>;
    case 'stopped':
      return <p className={styles.warn}>{t.farm.noteStopped}</p>;
  }
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
  const [counts, setCounts] = useState<UnitMap>(target.units);
  return (
    <Sheet open={open} onClose={onClose} title={t.farm.editTitle(target.target.name ?? t.map.barbarianVillage)}>
      <div className={styles.body}>
        <TemplatePicker
          counts={counts}
          home={home}
          onChange={(type, count) => {
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
            disabled={!hasUnits(counts) || change.isPending}
            onClick={() => change.mutate({ targetId: target.id, units: templateOf(counts) }, { onSuccess: onClose })}
          >
            {t.farm.save}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
