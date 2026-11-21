import { useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import {
  useGiveOrder,
  useOrderSheet,
  type OrderAlly,
  type OrderKind,
  type OrderSheet as OrderSheetData,
} from '../../api/queries/diplomacy';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { SegmentedControl } from '../../components/SegmentedControl';
import { Sheet } from '../../components/Sheet';
import { Switch } from '../../components/Switch';
import { useShowToast } from '../../components/toasts';
import { formatCoords, formatDateTimeExact, formatDecimal, formatDuration } from '../../game/format';
import { orderSeconds, orderWhat, ownerLine, preselected, targetName } from '../../game/orders';
import { useI18n } from '../../i18n';
import styles from './OrderSheet.module.css';

interface OrderSheetProps {
  /** The village the order is about; null while closed. */
  villageId: number | null;
  onClose: () => void;
}

/**
 * The order sheet (2.1 S3, 12-solo-and-allies §8, direction A): the target and its owner, the kinds
 * of order it takes, the allies as ticked rows with their travel time as the biggest figure, "Land
 * together with my attack" for an attack, one line that the lords decide, and one main button.
 * A sheet from below on the phone, a centred panel on the PC.
 */
export function OrderSheet({ villageId, onClose }: OrderSheetProps) {
  const { t } = useI18n();
  return (
    <Sheet open={villageId !== null} onClose={onClose} title={t.diplomacy.orders.title}>
      {villageId !== null && <OrderSheetBody key={villageId} villageId={villageId} onDone={onClose} />}
    </Sheet>
  );
}

function OrderSheetBody({ villageId, onDone }: { villageId: number; onDone: () => void }) {
  const { t } = useI18n();
  const sheet = useOrderSheet(villageId);
  if (sheet.isError) {
    return (
      <div className={styles.body}>
        <ErrorMessage>{errorMessage(t, sheet.error)}</ErrorMessage>
      </div>
    );
  }
  if (sheet.data === undefined) {
    return <p className={styles.body}>{t.common.loading}</p>;
  }
  return <OrderForm sheet={sheet.data} onDone={onDone} />;
}

function OrderForm({ sheet, onDone }: { sheet: OrderSheetData; onDone: () => void }) {
  const { t, language } = useI18n();
  const labels = t.diplomacy.orders;
  const give = useGiveOrder();
  const showToast = useShowToast();
  const ids = useId();
  const [kind, setKind] = useState<OrderKind>(sheet.kinds[0] ?? 'ATTACK');
  const [chosen, setChosen] = useState<ReadonlySet<number>>(() => preselected(sheet.allies));
  const [landTogether, setLandTogether] = useState(false);
  const target = sheet.target;
  const ownArrival = sheet.ownAttackArrivesAt === null ? null : Date.parse(sheet.ownAttackArrivesAt);
  const canLand = kind === 'ATTACK' && ownArrival !== null;
  const lordIds = sheet.allies.filter((ally) => chosen.has(ally.playerId)).map((ally) => ally.playerId);
  const hasAllies = sheet.allies.length > 0;

  const toggle = (id: number) =>
    setChosen((current) => {
      const next = new Set(current);
      if (!next.delete(id)) {
        next.add(id);
      }
      return next;
    });

  const submit = () =>
    give.mutate(
      { kind, villageId: target.id, lordIds, landTogether: canLand && landTogether },
      {
        onSuccess: () => {
          showToast({ text: labels.sent(lordIds.length), tone: 'success' });
          onDone();
        },
      },
    );

  return (
    <div className={styles.body}>
      <div className={styles.target}>
        <p className={styles.targetName}>
          {target.name ?? labels.barbarian} <span className={styles.coords}>{formatCoords(target.x, target.y)}</span>
        </p>
        <p className={styles.owner}>{ownerLine(t, sheet)}</p>
      </div>
      {sheet.kinds.length > 1 && (
        <SegmentedControl
          label={labels.kindsLabel}
          options={sheet.kinds.map((value) => ({ value, label: labels.kinds[value] }))}
          value={kind}
          onChange={setKind}
        />
      )}
      {hasAllies ? (
        <section aria-labelledby={`${ids}-who`}>
          <h3 id={`${ids}-who`} className={styles.whoTitle}>
            {labels.who}
          </h3>
          <ul className={styles.list}>
            {sheet.allies.map((ally) => (
              <AllyRow
                key={ally.playerId}
                ally={ally}
                kind={kind}
                checked={chosen.has(ally.playerId)}
                onToggle={() => toggle(ally.playerId)}
              />
            ))}
          </ul>
        </section>
      ) : (
        <p className={styles.hint}>{labels.noAllies}</p>
      )}
      {kind === 'ATTACK' && hasAllies && (
        <label className={styles.land} htmlFor={`${ids}-land`}>
          <span className={styles.landText}>
            <span className={styles.landTitle}>{labels.landTogether}</span>
            <span className={styles.landHint}>
              {ownArrival === null
                ? labels.landTogetherNone
                : labels.landTogetherHint(formatDateTimeExact(ownArrival, language))}
            </span>
          </span>
          <Switch id={`${ids}-land`} checked={canLand && landTogether} disabled={!canLand} onChange={setLandTogether} />
        </label>
      )}
      {kind === 'HOLD' && hasAllies && <p className={styles.hint}>{labels.holdHint}</p>}
      <p className={styles.decides}>{labels.decides}</p>
      {give.isError && <ErrorMessage>{errorMessage(t, give.error)}</ErrorMessage>}
      {hasAllies && (
        <Button
          type="button"
          className={styles.send}
          disabled={lordIds.length === 0 || give.isPending}
          onClick={submit}
        >
          {lordIds.length === 0 ? labels.sendNone : labels.send(lordIds.length)}
        </Button>
      )}
    </div>
  );
}

interface AllyRowProps {
  ally: OrderAlly;
  kind: OrderKind;
  checked: boolean;
  onToggle: () => void;
}

/**
 * One ally: the tick, its name, "arrives in · 13.4 fields · trust 64" and the order it carries now;
 * the travel time for this kind of order as the row's biggest figure (none for Hold).
 */
function AllyRow({ ally, kind, checked, onToggle }: AllyRowProps) {
  const { t, language } = useI18n();
  const labels = t.diplomacy.orders;
  const seconds = orderSeconds(ally, kind);
  const timed = kind !== 'HOLD';
  const reach = seconds === null ? labels.nothingHome[kind] : labels.arrivesIn;
  const facts = [
    timed ? reach : null,
    ally.fields === null ? null : labels.fields(formatDecimal(ally.fields, language)),
    labels.trust(ally.trust),
  ].filter((fact): fact is string => fact !== null && fact !== '');
  const open = ally.order !== null && ally.order.state !== 'REFUSED' ? ally.order : null;
  return (
    <li>
      <label className={styles.row}>
        <input type="checkbox" className={styles.check} checked={checked} onChange={onToggle} />
        <span className={styles.rowMain}>
          <span className={styles.rowName}>{ally.name}</span>
          <span className={styles.rowFacts}>{facts.join(' · ')}</span>
          {open !== null && (
            <span className={styles.rowNow}>{labels.now(orderWhat(t, open.kind, targetName(t, open.target)))}</span>
          )}
        </span>
        {timed && <span className={styles.time}>{seconds === null ? '—' : formatDuration(seconds)}</span>}
      </label>
    </li>
  );
}
