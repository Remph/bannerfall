import { useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useSendTrain, useTrainPreview, type Movement, type TrainRequest } from '../../api/queries/troops';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { UnitSummary } from '../../components/UnitSummary';
import { formatDecimal, formatDuration, formatFactor, formatWhenExact } from '../../game/format';
import { buildingTypes, type BuildingType } from '../../game/gameData';
import { nightBonusAt } from '../../game/nightBonus';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import styles from './RallyPage.module.css';
import { VillageRefText } from './VillageRefText';

interface TrainConfirmProps {
  villageId: number;
  /** The attacks with their units; the catapult targets are chosen here. */
  train: TrainRequest;
  /** The server accepted the train: its movements, in order. */
  onSent: (movements: Movement[]) => void;
  onBack: () => void;
}

/**
 * The confirmation sheet of an attack train (M37, 02-game-rules §6.1): the server's preview (target,
 * distance, the one travel time and arrival of the whole train), then the attacks in the order they
 * land, each with its units, its catapult target and the rules it breaks. Confirm sends all of them
 * and stays disabled while any attack breaks a rule.
 */
export function TrainConfirm({ villageId, train, onSent, onBack }: TrainConfirmProps) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const me = useCurrentAccount();
  const selectId = useId();
  const [catapultTargets, setCatapultTargets] = useState<BuildingType[]>(() => train.attacks.map(() => 'HEADQUARTERS'));
  const preview = useTrainPreview(villageId, train);
  const send = useSendTrain(villageId);
  const world = useWorld();

  if (preview.isPending) {
    return <p className={styles.muted}>{t.rally.checking}</p>;
  }
  if (preview.isError) {
    // only a missing target village is an error (404 TARGET_NOT_FOUND); broken rules come per attack
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

  const { targetVillage, distance, travelSeconds, attacks } = preview.data;
  const broken = attacks.some((attack) => attack.errors.length > 0);
  const nobles = train.attacks.some((attack) => (attack.units.NOBLE ?? 0) > 0);
  // as for a single attack (02 §6): barbarian villages only end the protection with nobles
  const endsProtection = Date.parse(me.protectedUntil) > now && (targetVillage.playerName !== null || nobles);
  // the night bonus (1.4) at the train's one arrival if it were sent now
  const night =
    travelSeconds !== null
      ? nightBonusAt(world.data?.nightBonus, now + travelSeconds * 1000, targetVillage.playerId !== null)
      : null;

  function confirm() {
    send.mutate(
      {
        target: train.target,
        attacks: train.attacks.map((attack, index) => ({
          units: attack.units,
          catapultTarget: (attack.units.CATAPULT ?? 0) > 0 ? (catapultTargets[index] ?? 'HEADQUARTERS') : null,
        })),
      },
      { onSuccess: onSent },
    );
  }

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
              <dt>{t.rally.arrival}</dt>
              <dd>{formatWhenExact(now + travelSeconds * 1000, now, language)}</dd>
            </div>
          </>
        )}
      </dl>
      <p className={styles.notice}>{t.rally.trainPace}</p>
      <ol className={styles.trainConfirmList}>
        {train.attacks.map((attack, index) => {
          const errors = attacks[index]?.errors ?? [];
          const withCatapults = (attack.units.CATAPULT ?? 0) > 0;
          return (
            // the attacks are a fixed list while the sheet is open: the index is their identity
            <li key={index} className={styles.trainConfirmAttack}>
              <span className={styles.trainNumber}>{t.rally.trainAttack(index + 1)}</span>
              <UnitSummary units={attack.units} />
              {withCatapults && (
                <div className={styles.select}>
                  <label htmlFor={`${selectId}-${index}`}>{t.rally.catapultTarget}</label>
                  <select
                    id={`${selectId}-${index}`}
                    value={catapultTargets[index] ?? 'HEADQUARTERS'}
                    onChange={(event) => {
                      const chosen = buildingTypes.find((type) => type === event.target.value) ?? 'HEADQUARTERS';
                      setCatapultTargets((previous) => previous.map((old, i) => (i === index ? chosen : old)));
                    }}
                  >
                    {buildingTypes.map((type) => (
                      <option key={type} value={type}>
                        {t.buildings[type]}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              {errors.length > 0 && (
                <ul className={styles.errors} role="alert">
                  {errors.map((code) => (
                    <li key={code}>{t.errors[code]}</li>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ol>
      {endsProtection && <p className={styles.notice}>{t.rally.endsProtection}</p>}
      {night !== null && (
        <p className={`${styles.notice} ${styles.nightNotice}`}>
          <Icon name="moon" /> {t.rally.nightBonus(formatFactor(night, language))}
        </p>
      )}
      {send.isError && !broken && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
      <div className={styles.confirmActions}>
        <Button type="button" variant="secondary" onClick={onBack}>
          {t.rally.back}
        </Button>
        <Button
          type="button"
          variant="attack"
          disabled={broken || send.isPending || preview.isFetching}
          onClick={confirm}
        >
          {send.isPending ? t.rally.sending : t.rally.confirmTrain(train.attacks.length)}
        </Button>
      </div>
    </div>
  );
}
