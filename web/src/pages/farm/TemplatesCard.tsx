import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import type { FarmTarget } from '../../api/queries/farm';
import { useDeleteFarmTemplate, useFarmTemplates, useSaveFarmTemplate } from '../../api/queries/farmTemplates';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { MoreMenu } from '../../components/MoreMenu';
import { Sheet } from '../../components/Sheet';
import { UnitSummary } from '../../components/UnitSummary';
import { hasUnits, templateOf } from '../../game/farm';
import { nextFreeLetter, type TemplateLetter } from '../../game/farmTemplates';
import type { UnitMap } from '../../game/troops';
import { useT } from '../../i18n';
import styles from './Farm.module.css';
import { TemplateLetterBadge } from './TemplateButtons';
import { TemplatePicker } from './TemplatePicker';

/**
 * The player's templates A to E (1.4), shared by all his villages: one row each with its units and
 * how many targets of this village's list use it; change and delete behind "…", and the next free
 * letter offers to be created.
 */
export function TemplatesCard({ targets, home }: { targets: FarmTarget[]; home: UnitMap }) {
  const t = useT();
  const templates = useFarmTemplates();
  const remove = useDeleteFarmTemplate();
  const [editing, setEditing] = useState<TemplateLetter>();
  const list = templates.data ?? [];
  const editUnits = list.find((template) => template.letter === editing)?.units ?? {};
  const nextFree = nextFreeLetter(list);
  return (
    <Card title={t.farm.templatesTitle} id="templates">
      <div className={styles.body}>
        <p className={styles.hint}>{t.farm.templatesHint}</p>
        {templates.isError && <ErrorMessage>{errorMessage(t, templates.error)}</ErrorMessage>}
      </div>
      {list.length > 0 && (
        <ul className={styles.list}>
          {list.map((template) => (
            <li key={template.letter}>
              <div className={styles.templateRow}>
                <TemplateLetterBadge letter={template.letter} label={t.farm.templateLabel(template.letter)} />
                <div className={styles.templateFacts}>
                  <UnitSummary units={template.units} />
                  <p className={styles.meta}>
                    {t.farm.templateUsed(targets.filter((target) => target.template === template.letter).length)}
                  </p>
                </div>
                <MoreMenu
                  label={t.farm.templateMore(template.letter)}
                  items={[
                    { label: t.farm.edit, icon: 'pencil', onSelect: () => setEditing(template.letter) },
                    {
                      label: t.farm.templateDelete,
                      icon: 'trash',
                      danger: true,
                      disabled: remove.isPending,
                      onSelect: () => remove.mutate(template.letter),
                    },
                  ]}
                />
              </div>
            </li>
          ))}
        </ul>
      )}
      {(nextFree !== undefined || remove.isError) && (
        <div className={styles.body}>
          {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
          {nextFree !== undefined && templates.isSuccess && (
            <button type="button" className={styles.newTemplate} onClick={() => setEditing(nextFree)}>
              <Icon name="plus" />
              {t.farm.templateNew(nextFree)}
            </button>
          )}
        </div>
      )}
      <EditTemplateSheet
        // a fresh form for each letter, from its saved units
        key={editing ?? ''}
        letter={editing}
        units={editUnits}
        home={home}
        onClose={() => setEditing(undefined)}
      />
    </Card>
  );
}

interface EditTemplateSheetProps {
  letter?: TemplateLetter;
  units: UnitMap;
  home: UnitMap;
  onClose: () => void;
}

/** A template's units; saving changes every target that uses it, in all villages. */
function EditTemplateSheet({ letter, units, home, onClose }: EditTemplateSheetProps) {
  const t = useT();
  const save = useSaveFarmTemplate();
  const [counts, setCounts] = useState<UnitMap>(units);
  return (
    <Sheet open={letter !== undefined} onClose={onClose} title={t.farm.templateEditTitle(letter ?? '')}>
      <div className={styles.body}>
        <p className={styles.hint}>{t.farm.templatesHint}</p>
        <TemplatePicker
          counts={counts}
          home={home}
          onChange={(type, count) => {
            setCounts((current) => ({ ...current, [type]: count }));
            save.reset();
          }}
        />
        {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={letter === undefined || !hasUnits(counts) || save.isPending}
            onClick={() => {
              if (letter !== undefined) {
                save.mutate({ letter, units: templateOf(counts) }, { onSuccess: onClose });
              }
            }}
          >
            {t.farm.save}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
