import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { troopsOfChoice, useAddFarmTarget } from '../../api/queries/farm';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { parseCoords } from '../../game/coords';
import { hasUnits } from '../../game/farm';
import type { UnitMap } from '../../game/troops';
import { useI18n } from '../../i18n';
import styles from './Farm.module.css';
import { TemplateChoice } from './TemplateChoice';
import { useFarmTemplates } from '../../api/queries/farmTemplates';
import { unitsOfChoice, type TemplateLetter } from '../../game/farmTemplates';

interface AddTargetCardProps {
  villageId: number;
  home: UnitMap;
  /** "48|52" from the map's or a report's "Add to farm list" button, or ''. */
  initialTarget: string;
  /** The units an attack report sent, or {} (M38). */
  initialUnits?: UnitMap;
}

/** Adds a target by its coordinates, with one of the templates or its own units (1.4). */
export function AddTargetCard({ villageId, home, initialTarget, initialUnits = {} }: AddTargetCardProps) {
  const { t } = useI18n();
  const add = useAddFarmTarget(villageId);
  const [target, setTarget] = useState(initialTarget);
  const [counts, setCounts] = useState<UnitMap>(initialUnits);
  const templates = useFarmTemplates().data ?? [];
  const [picked, setPicked] = useState<TemplateLetter | null>();
  // until the player picks: a report's units as own troops, otherwise the first template (they may
  // still be loading)
  const letter = picked !== undefined ? picked : hasUnits(initialUnits) ? null : (templates[0]?.letter ?? null);
  const units = unitsOfChoice(letter, templates, counts);
  const coords = parseCoords(target);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (coords === undefined || !hasUnits(units)) {
      return;
    }
    add.mutate(
      { target: coords, troops: troopsOfChoice(letter, counts) },
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
        <TemplateChoice
          templates={templates}
          value={letter}
          onChange={(next) => {
            setPicked(next);
            add.reset();
          }}
          counts={counts}
          home={home}
          onCounts={(type, count) => {
            setCounts((current) => ({ ...current, [type]: count }));
            add.reset();
          }}
        />
        {add.isError && <ErrorMessage>{errorMessage(t, add.error)}</ErrorMessage>}
        <div>
          <Button type="submit" disabled={coords === undefined || !hasUnits(units) || add.isPending}>
            {t.farm.add}
          </Button>
        </div>
      </form>
    </Card>
  );
}
