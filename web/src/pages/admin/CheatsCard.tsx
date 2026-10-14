import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useAdminPlayers, useHostCheat, type HostCheat } from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { formatCoords } from '../../game/format';
import { unitTypes, type UnitType } from '../../game/gameData';
import { useT } from '../../i18n';
import styles from './AdminPage.module.css';

/**
 * The host cheats (08-host-console §3) on any village: picked through a player, or by its id (NPC
 * lords and barbarian villages have ids too, e.g. from the audit log). Only shown while the server
 * has them switched on; each one lands in the audit log.
 */
export function CheatsCard() {
  const t = useT();
  const labels = t.admin.cheats;
  const players = useAdminPlayers();
  const cheat = useHostCheat();
  const [playerId, setPlayerId] = useState('');
  const [villageId, setVillageId] = useState('');
  const [amount, setAmount] = useState('10000');
  const [unit, setUnit] = useState<UnitType>('SPEARMAN');
  const [count, setCount] = useState('100');

  const villages = players.data?.find((p) => String(p.playerId) === playerId)?.villages ?? [];
  const village = Number(villageId);
  const hasVillage = villageId !== '' && Number.isInteger(village) && village > 0;
  const amountNumber = Number(amount);
  const countNumber = Number(count);

  function run(next: HostCheat) {
    cheat.reset();
    cheat.mutate(next);
  }

  return (
    <Card title={labels.title}>
      <div className={styles.stack}>
        <p className={styles.hint}>{labels.hint}</p>
        <div className={styles.fields}>
          <div className={styles.select}>
            <label htmlFor="cheat-player">{labels.player}</label>
            <select
              id="cheat-player"
              value={playerId}
              onChange={(event) => {
                setPlayerId(event.target.value);
                setVillageId('');
              }}
            >
              <option value="">{labels.choose}</option>
              {players.data
                ?.filter((p) => !p.deleted)
                .map((p) => (
                  <option key={p.playerId} value={p.playerId}>
                    {p.name}
                  </option>
                ))}
            </select>
          </div>
          <div className={styles.select}>
            <label htmlFor="cheat-village">{labels.village}</label>
            <select
              id="cheat-village"
              value={villages.some((v) => String(v.id) === villageId) ? villageId : ''}
              onChange={(event) => setVillageId(event.target.value)}
            >
              <option value="">{labels.choose}</option>
              {villages.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name} {formatCoords(v.x, v.y)}
                </option>
              ))}
            </select>
          </div>
          <TextField
            label={labels.villageId}
            type="number"
            min={1}
            step={1}
            value={villageId}
            onChange={(event) => setVillageId(event.target.value)}
          />
        </div>

        <div className={styles.inline}>
          <TextField
            label={labels.resources}
            type="number"
            min={0}
            max={100_000_000}
            step={1000}
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
          />
          <Button
            type="button"
            variant="secondary"
            disabled={!hasVillage || !Number.isInteger(amountNumber) || amountNumber < 0 || cheat.isPending}
            onClick={() => run({ kind: 'resources', villageId: village, amount: amountNumber })}
          >
            {labels.give}
          </Button>
        </div>

        <div className={styles.inline}>
          <div className={styles.select}>
            <label htmlFor="cheat-unit">{labels.unit}</label>
            <select id="cheat-unit" value={unit} onChange={(event) => setUnit(event.target.value as UnitType)}>
              {unitTypes.map((type) => (
                <option key={type} value={type}>
                  {t.units[type]}
                </option>
              ))}
            </select>
          </div>
          <TextField
            label={labels.count}
            type="number"
            min={0}
            max={100_000}
            step={10}
            value={count}
            onChange={(event) => setCount(event.target.value)}
          />
          <Button
            type="button"
            variant="secondary"
            disabled={!hasVillage || !Number.isInteger(countNumber) || countNumber < 0 || cheat.isPending}
            onClick={() => run({ kind: 'units', villageId: village, units: { [unit]: countNumber } })}
          >
            {labels.addUnits}
          </Button>
        </div>

        <div className={styles.actions}>
          <Button
            type="button"
            variant="secondary"
            disabled={!hasVillage || cheat.isPending}
            onClick={() => run({ kind: 'finish-orders', villageId: village })}
          >
            {labels.finishOrders}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={!hasVillage || cheat.isPending}
            onClick={() => run({ kind: 'finish-movements', villageId: village })}
          >
            {labels.finishMovements}
          </Button>
        </div>
        {cheat.isError && <ErrorMessage>{errorMessage(t, cheat.error)}</ErrorMessage>}
        {cheat.isSuccess && <p className={styles.saved}>{labels.done}</p>}
      </div>
    </Card>
  );
}
