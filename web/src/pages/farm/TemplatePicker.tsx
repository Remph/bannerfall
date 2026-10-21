import { useWorld } from '../../api/queries/world';
import { Icon } from '../../components/Icon';
import { UnitCountInput } from '../../components/UnitCountInput';
import { FARM_TEMPLATE_MAX, farmUnitTypes } from '../../game/farm';
import { isArcher, type UnitType } from '../../game/gameData';
import type { UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import styles from './Farm.module.css';

interface TemplatePickerProps {
  counts: UnitMap;
  /** The units at home: the "all 20" chip. The template may hold more (more will be trained). */
  home: UnitMap;
  onChange: (type: UnitType, count: number) => void;
}

/** How many units of each type the template sends: one row per type with − / + and the count. */
export function TemplatePicker({ counts, home, onChange }: TemplatePickerProps) {
  const t = useT();
  // archers only in a world with them (1.6), or when the template still holds some
  const archers = useWorld().data?.archersEnabled === true;
  const types = farmUnitTypes.filter((type) => archers || !isArcher(type) || (counts[type] ?? 0) > 0);
  return (
    <ul className={styles.pickList}>
      {types.map((type) => (
        <li key={type} className={styles.pickRow}>
          <span className={styles.pickName}>
            <span className={styles.unitIcon}>
              <Icon name={type} />
            </span>
            {t.units[type]}
          </span>
          <span className={styles.pickInput}>
            <UnitCountInput
              value={counts[type] ?? 0}
              onChange={(count) => onChange(type, count)}
              max={home[type] ?? 0}
              // + goes past the units at home: the template is what the player wants to send, the
              // units may still be in training (the server allows up to FARM_TEMPLATE_MAX)
              stepMax={FARM_TEMPLATE_MAX}
              limit={FARM_TEMPLATE_MAX}
              label={t.farm.countLabel(t.units[type])}
              maxLabel={t.rally.all}
            />
          </span>
        </li>
      ))}
    </ul>
  );
}
