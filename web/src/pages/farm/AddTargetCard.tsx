import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useAddFarmTarget } from '../../api/queries/farm';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { parseCoords } from '../../game/coords';
import { hasUnits, templateOf } from '../../game/farm';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import styles from './Farm.module.css';
import { TemplatePicker } from './TemplatePicker';

interface AddTargetCardProps {
  villageId: number;
  home: UnitMap;
  /** "48|52" from the map's or a report's "Add to farm list" button, or ''. */
  initialTarget: string;
  /** The units an attack report sent, or {} (M38). */
  initialUnits?: UnitMap;
}

/** Adds a target by its coordinates, with the units that are usually sent to it. */
export function AddTargetCard({ villageId, home, initialTarget, initialUnits = {} }: AddTargetCardProps) {
  const { t } = useI18n();
  const add = useAddFarmTarget(villageId);
  const [target, setTarget] = useState(initialTarget);
  const [counts, setCounts] = useState<UnitMap>(initialUnits);
  const coords = parseCoords(target);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (coords === undefined || !hasUnits(counts)) {
      return;
    }
    add.mutate(
      { target: coords, units: templateOf(counts) },
      {
        onSuccess: () => {
          setTarget('');
          setCounts({});
        },
      },
    );
  }

  return (
    <Card title={t.farm.addTitle}>
      <form className={styles.body} onSubmit={submit}>
        <TextField
          label={t.rally.target}
          hint={t.rally.targetHint}
          value={target}
          inputMode="text"
          autoComplete="off"
          onChange={(event) => {
            setTarget(event.target.value);
            add.reset();
          }}
        />
        <TemplatePicker
          counts={counts}
          home={home}
          onChange={(type, count) => {
            setCounts((current) => ({ ...current, [type]: count }));
            add.reset();
          }}
        />
        {add.isError && <ErrorMessage>{errorMessage(t, add.error)}</ErrorMessage>}
        <div>
          <Button type="submit" disabled={coords === undefined || !hasUnits(counts) || add.isPending}>
            {t.farm.add}
          </Button>
        </div>
      </form>
    </Card>
  );
}
