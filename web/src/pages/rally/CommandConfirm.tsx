import { useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useCommandPreview, useSendCommand, type CommandRequest, type Movement } from '../../api/queries/troops';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { UnitSummary } from '../../components/UnitSummary';
import { formatDecimal, formatDuration, formatWhenExact } from '../../game/format';
import { buildingTypes, type BuildingType } from '../../game/gameData';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './RallyPage.module.css';
import { VillageRefText } from './VillageRefText';

interface CommandConfirmProps {
  villageId: number;
  command: CommandRequest;
  /** The server accepted the command. */
  onSent: (movement: Movement) => void;
  onBack: () => void;
}

/**
 * The content of the confirmation sheet: the server's preview of the command (target village,
 * distance, travel time, arrival) and every rule it breaks. Confirm stays disabled while there are
 * any. An attack with catapults also chooses their target here (02-game-rules §6, default Town Hall).
 */
export function CommandConfirm({ villageId, command, onSent, onBack }: CommandConfirmProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const me = useCurrentAccount();
  const selectId = useId();
  const [catapultTarget, setCatapultTarget] = useState<BuildingType>('HEADQUARTERS');
  const preview = useCommandPreview(villageId, command);
  const send = useSendCommand(villageId);
  const attack = command.kind === 'ATTACK';
  const withCatapults = attack && (command.units.CATAPULT ?? 0) > 0;

  if (preview.isPending) {
    return <p className={styles.muted}>{t.rally.checking}</p>;
  }
  if (preview.isError) {
    // only a missing target village is an error (404 TARGET_NOT_FOUND); broken rules come in `errors`
    return (
      <div className={styles.confirm}>
        <ErrorMessage>{errorMessage(t, preview.error)}</ErrorMessage>
        <div className={styles.confirmActions}>
          <Button type="button" variant="secondary" onClick={onBack}>
            {t.rally.back}
          </Button>
        </div>
      </div>
    );
  }

  const { targetVillage, distance, travelSeconds, errors } = preview.data;
  // Barbarian villages (no player name) can be attacked without losing the protection, except with
  // nobles (02 §6).
  const endsProtection =
    Date.parse(me.protectedUntil) > now && (targetVillage.playerName !== null || (command.units.NOBLE ?? 0) > 0);
  return (
    <div className={styles.confirm}>
      <p className={styles.confirmTarget}>
        <VillageRefText village={targetVillage} currentVillageId={villageId} link={false} />
        {targetVillage.playerName !== null && <span className={styles.player}> · {targetVillage.playerName}</span>}
      </p>
      <dl className={styles.facts}>
        <div>
          <dt>{t.map.distance}</dt>
          <dd>{t.map.fields(formatDecimal(distance, language))}</dd>
        </div>
        {travelSeconds !== null && (
          <>
            <div>
              <dt>{t.rally.travelTime}</dt>
              <dd>{formatDuration(travelSeconds)}</dd>
            </div>
            <div>
              {/* if it were sent now: ticks with the clock while the sheet is open */}
              <dt>{t.rally.arrival}</dt>
              <dd>{formatWhenExact(now + travelSeconds * 1000, now, language)}</dd>
            </div>
          </>
        )}
      </dl>
      <UnitSummary units={command.units} />
      {command.kind === 'SUPPORT' && (command.units.PALADIN ?? 0) > 0 && (
        <p className={styles.notice}>{t.rally.paladinLeads}</p>
      )}
      {withCatapults && (
        <div className={styles.select}>
          <label htmlFor={selectId}>{t.rally.catapultTarget}</label>
          <select
            id={selectId}
            value={catapultTarget}
            // find() turns the plain string back into a BuildingType (no unchecked cast)
            onChange={(event) =>
              setCatapultTarget(buildingTypes.find((type) => type === event.target.value) ?? 'HEADQUARTERS')
            }
          >
            {buildingTypes.map((type) => (
              <option key={type} value={type}>
                {t.buildings[type]}
              </option>
            ))}
          </select>
        </div>
      )}
      {attack && endsProtection && <p className={styles.notice}>{t.rally.endsProtection}</p>}
      {errors.length > 0 && (
        <ul className={styles.errors} role="alert">
          {errors.map((code) => (
            <li key={code}>{t.errors[code]}</li>
          ))}
        </ul>
      )}
      {/* a failed send reloads the preview; its errors say why, so the send error only shows without them */}
      {send.isError && errors.length === 0 && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" onClick={onBack}>
          {t.rally.back}
        </Button>
        <Button
          type="button"
          variant={attack ? 'attack' : 'primary'}
          disabled={errors.length > 0 || send.isPending || preview.isFetching}
          onClick={() =>
            send.mutate({ ...command, catapultTarget: withCatapults ? catapultTarget : null }, { onSuccess: onSent })
          }
        >
          {send.isPending ? t.rally.sending : t.rally.confirm[command.kind]}
        </Button>
      </div>
    </div>
  );
}
