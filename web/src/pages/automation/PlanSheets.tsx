import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useDeleteBuildPlan, useSaveBuildPlan } from '../../api/queries/automation';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import {
  MAX_PLAN_LINES,
  MAX_PLAN_NAME,
  MINES,
  appendMines,
  appendNextLevel,
  changeLevel,
  isSavable,
  linesFromLevels,
  minesTopLevel,
  missingPerLine,
  moveLine,
  reachedAbove,
  removeLine,
  setRecruitLine,
  type BuildPlan,
  type PlanBuilding,
  type PlanLine,
} from '../../game/buildPlans';
import { nextFreeLetter, type TemplateLetter } from '../../game/farmTemplates';
import { byBuildingOrder } from '../../game/gameData';
import type { RecruitTemplate } from '../../game/recruitTemplates';
import { useT } from '../../i18n';
import styles from './AutomationPage.module.css';
import { PlanCodeSheet, type CodeMode } from './PlanCodeSheet';
import { PlanLineView } from './PlanLineView';

interface PlanPickerProps {
  open: boolean;
  village: Village;
  plans: readonly BuildPlan[];
  current: TemplateLetter | null;
  onPick: (letter: TemplateLetter | null) => void;
  onCreate: (letter: TemplateLetter) => void;
  onClose: () => void;
}

/** Picking this village's plan: the player's plans, a new one while a letter is free, or none. */
export function PlanPicker({ open, village, plans, current, onPick, onCreate, onClose }: PlanPickerProps) {
  const t = useT();
  const free = nextFreeLetter(plans.map((plan) => ({ letter: plan.letter, units: {} })));
  return (
    <Sheet open={open} onClose={onClose} title={t.automation.pickTitle(village.name)}>
      <ul className={styles.choose}>
        {plans.map((plan) => (
          <li key={plan.letter}>
            <button
              type="button"
              className={styles.chooseRow}
              aria-pressed={plan.letter === current}
              onClick={() => onPick(plan.letter)}
            >
              <span className={styles.letter} aria-hidden="true">
                {plan.letter}
              </span>
              <span className={styles.rowText}>
                <strong>{plan.name}</strong>
                <span className={styles.muted}>{t.automation.lineCount(plan.lines.length)}</span>
              </span>
              {plan.letter === current && <Icon name="check" className={styles.inlineIcon} />}
            </button>
          </li>
        ))}
        {free !== undefined && (
          <li>
            <button type="button" className={styles.chooseRow} onClick={() => onCreate(free)}>
              <span className={styles.letterEmpty} aria-hidden="true">
                <Icon name="plus" />
              </span>
              <span className={styles.rowText}>
                <strong>{t.automation.newPlan(free)}</strong>
                <span className={styles.muted}>{t.automation.newPlanHint}</span>
              </span>
            </button>
          </li>
        )}
        {current !== null && (
          <li>
            <button type="button" className={styles.chooseRow} onClick={() => onPick(null)}>
              <span className={styles.letterEmpty} aria-hidden="true">
                <Icon name="close" />
              </span>
              <span className={styles.rowText}>
                <strong>{t.automation.noPlan}</strong>
                <span className={styles.muted}>{t.automation.noPlanHint}</span>
              </span>
            </button>
          </li>
        )}
      </ul>
    </Sheet>
  );
}

interface PlanEditorProps {
  letter: TemplateLetter;
  /** Undefined for a new plan. */
  plan: BuildPlan | undefined;
  /** For "take this village's levels". */
  village: Village;
  buildings: readonly PlanBuilding[];
  templates: readonly RecruitTemplate[];
  onSaved: () => void;
  onClose: () => void;
}

/** What is open below the lines: the buildings to add, or the recruit templates. */
type Adding = 'none' | 'building' | 'recruit';

/**
 * The plan editor (a full-screen sheet): the name and the lines from top to bottom, each with −/+
 * for its level; "Change order" swaps them for up, down and remove. A line that lacks a requirement
 * says so, and the plan can't be saved until it is fixed.
 */
export function PlanEditor({ letter, plan, village, buildings, templates, onSaved, onClose }: PlanEditorProps) {
  const t = useT();
  const save = useSaveBuildPlan();
  const remove = useDeleteBuildPlan();
  const [name, setName] = useState(plan?.name ?? '');
  const [lines, setLines] = useState<readonly PlanLine[]>(plan?.lines ?? []);
  const [reorder, setReorder] = useState(false);
  const [adding, setAdding] = useState<Adding>('none');
  const [deleting, setDeleting] = useState(false);
  const [code, setCode] = useState<CodeMode>();
  const change = (next: readonly PlanLine[]) => {
    setLines(next);
    save.reset();
  };
  const missing = missingPerLine(lines, buildings);
  const trimmed = name.trim();
  return (
    <Sheet open onClose={onClose} title={t.automation.editTitle(letter)} fullScreen>
      <div className={styles.edit}>
        <TextField
          label={t.automation.name}
          hint={t.automation.nameHint}
          value={name}
          maxLength={MAX_PLAN_NAME}
          onChange={(event) => {
            setName(event.target.value);
            save.reset();
          }}
        />
        <div className={styles.editBar}>
          <span className={styles.muted}>{t.automation.linesHint(lines.length)}</span>
        </div>
        {lines.length === 0 ? (
          <p className={styles.muted}>{t.automation.empty}</p>
        ) : (
          <ol className={styles.editLines}>
            {lines.map((line, index) => (
              <PlanLineView
                // lines are a list without ids; the buttons act on the index
                key={index}
                line={line}
                index={index}
                templates={templates}
              >
                <LineControls
                  line={line}
                  index={index}
                  count={lines.length}
                  reorder={reorder}
                  buildings={buildings}
                  onChange={(next) => change(next(lines))}
                />
                {(missing[index]?.length ?? 0) > 0 && (
                  <p className={styles.problem}>
                    {t.automation.needs(
                      (missing[index] ?? [])
                        .map((need) => t.townHall.requirement(t.buildings[need.building], need.level))
                        .join(', '),
                    )}
                  </p>
                )}
              </PlanLineView>
            ))}
          </ol>
        )}
        {/* below the lines: after 30 or more, a tap on "Done" needs no scrolling back up */}
        {lines.length > 1 && (
          <button type="button" className={styles.link} onClick={() => setReorder(!reorder)}>
            {reorder ? t.automation.reorderDone : t.automation.reorder}
          </button>
        )}
        {lines.length > MAX_PLAN_LINES && <ErrorMessage>{t.automation.tooMany(MAX_PLAN_LINES)}</ErrorMessage>}
        <AddBox
          adding={adding}
          lines={lines}
          buildings={buildings}
          templates={templates}
          onAdding={setAdding}
          onChange={change}
        />
        <div className={styles.codeLinks}>
          <button
            type="button"
            className={styles.link}
            onClick={() => change(linesFromLevels(village.buildings, buildings))}
          >
            <Icon name="village" /> {t.automation.fromVillage}
          </button>
          {lines.length > 0 && (
            <button type="button" className={styles.link} onClick={() => setCode('share')}>
              <Icon name="code" /> {t.automation.code.share}
            </button>
          )}
          <button type="button" className={styles.link} onClick={() => setCode('import')}>
            <Icon name="code" /> {t.automation.code.import}
          </button>
        </div>
        {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
        <div className={styles.actions}>
          {plan !== undefined && (
            <Button type="button" variant="danger" onClick={() => setDeleting(true)}>
              {t.automation.delete}
            </Button>
          )}
          <Button
            type="button"
            disabled={!isSavable(trimmed, lines, buildings) || save.isPending}
            onClick={() =>
              save.mutate(
                { letter, name: trimmed, lines },
                {
                  onSuccess: () => {
                    onSaved();
                    onClose();
                  },
                },
              )
            }
          >
            {t.automation.save}
          </Button>
        </div>
      </div>
      <PlanCodeSheet
        mode={code}
        name={name}
        lines={lines}
        buildings={buildings}
        onApply={(codeName, codeLines) => {
          setName(codeName);
          change(codeLines);
          setReorder(false);
          setCode(undefined);
        }}
        onClose={() => setCode(undefined)}
      />
      <ConfirmSheet
        open={deleting}
        title={t.automation.deleteTitle(letter)}
        text={t.automation.deleteText}
        confirmLabel={t.automation.delete}
        danger
        pending={remove.isPending}
        error={remove.error}
        onConfirm={() => remove.mutate(letter, { onSuccess: onClose })}
        onClose={() => {
          setDeleting(false);
          remove.reset();
        }}
      />
    </Sheet>
  );
}

interface LineControlsProps {
  line: PlanLine;
  index: number;
  count: number;
  reorder: boolean;
  buildings: readonly PlanBuilding[];
  /** Gets the change as a function of the lines. */
  onChange: (next: (lines: readonly PlanLine[]) => PlanLine[]) => void;
}

/** −/+ for the level (× for the recruit line), or up, down and remove while reordering. */
function LineControls({ line, index, count, reorder, buildings, onChange }: LineControlsProps) {
  const t = useT();
  const number = index + 1;
  if (reorder) {
    return (
      <span className={styles.stepper}>
        <button
          type="button"
          aria-label={t.automation.moveUp(number)}
          disabled={index === 0}
          onClick={() => onChange((lines) => moveLine(lines, index, -1))}
        >
          <Icon name="chevronUp" />
        </button>
        <button
          type="button"
          aria-label={t.automation.moveDown(number)}
          disabled={index === count - 1}
          onClick={() => onChange((lines) => moveLine(lines, index, 1))}
        >
          <Icon name="chevronDown" />
        </button>
        <button
          type="button"
          aria-label={t.automation.remove(number)}
          onClick={() => onChange((lines) => removeLine(lines, index))}
        >
          <Icon name="trash" />
        </button>
      </span>
    );
  }
  if (line.kind === 'recruit') {
    return (
      <span className={styles.stepper}>
        <button
          type="button"
          aria-label={t.automation.remove(number)}
          onClick={() => onChange((lines) => removeLine(lines, index))}
        >
          <Icon name="close" />
        </button>
      </span>
    );
  }
  const name = line.kind === 'mines' ? t.automation.allMines : t.buildings[line.building];
  const maxLevel =
    line.kind === 'mines'
      ? minesTopLevel(buildings)
      : (buildings.find((info) => info.building === line.building)?.maxLevel ?? line.level);
  return (
    <span className={styles.stepper}>
      <button
        type="button"
        aria-label={t.automation.levelDown(name)}
        disabled={line.level <= 1}
        onClick={() => onChange((lines) => changeLevel(lines, index, -1, buildings))}
      >
        <Icon name="minus" />
      </button>
      <button
        type="button"
        aria-label={t.automation.levelUp(name)}
        disabled={line.level >= maxLevel}
        onClick={() => onChange((lines) => changeLevel(lines, index, 1, buildings))}
      >
        <Icon name="plus" />
      </button>
    </span>
  );
}

interface AddBoxProps {
  adding: Adding;
  lines: readonly PlanLine[];
  buildings: readonly PlanBuilding[];
  templates: readonly RecruitTemplate[];
  onAdding: (adding: Adding) => void;
  onChange: (lines: readonly PlanLine[]) => void;
}

/** "+ Building" opens the buildings (each tap adds the next level), "+ Start recruiting" the templates. */
function AddBox({ adding, lines, buildings, templates, onAdding, onChange }: AddBoxProps) {
  const t = useT();
  if (adding === 'none') {
    return (
      <div className={styles.addRow}>
        <Button type="button" variant="secondary" size="small" onClick={() => onAdding('building')}>
          {t.automation.addBuilding}
        </Button>
        <Button type="button" variant="secondary" size="small" onClick={() => onAdding('recruit')}>
          {t.automation.addRecruit}
        </Button>
      </div>
    );
  }
  if (adding === 'recruit') {
    return (
      <div className={styles.addBox}>
        <p className={styles.addTitle}>{t.automation.recruitTitle}</p>
        <p className={styles.muted}>{t.automation.recruitHint}</p>
        {templates.length === 0 ? (
          <p className={styles.muted}>{t.automation.noTemplates}</p>
        ) : (
          <ul className={styles.choose}>
            {templates.map((template) => (
              <li key={template.letter}>
                <button
                  type="button"
                  className={styles.chooseRow}
                  onClick={() => {
                    onChange(setRecruitLine(lines, template.letter));
                    onAdding('none');
                  }}
                >
                  <span className={styles.letter} aria-hidden="true">
                    {template.letter}
                  </span>
                  <strong className={styles.rowText}>{template.name}</strong>
                </button>
              </li>
            ))}
          </ul>
        )}
        <button type="button" className={styles.link} onClick={() => onAdding('none')}>
          {t.automation.addClose}
        </button>
      </div>
    );
  }
  const reached = reachedAbove(lines, lines.length, buildings);
  const bundle = appendMines(lines, buildings);
  const offered = buildings.filter((info) => info.available).sort((a, b) => byBuildingOrder(a.building, b.building));
  return (
    <div className={styles.addBox}>
      <p className={styles.addTitle}>{t.automation.addTitle}</p>
      <div className={styles.addGrid}>
        {offered.map((info) => (
          <button
            key={info.building}
            type="button"
            className={styles.addTile}
            aria-label={t.buildings[info.building]}
            title={t.buildings[info.building]}
            disabled={(reached[info.building] ?? 0) >= info.maxLevel || lines.length >= MAX_PLAN_LINES}
            onClick={() => onChange(appendNextLevel(lines, info.building, buildings))}
          >
            <Icon name={info.building} />
          </button>
        ))}
        <button
          type="button"
          className={`${styles.addTile} ${styles.addTileWide}`}
          aria-label={t.automation.addMines}
          title={t.automation.addMines}
          disabled={bundle.length === lines.length || bundle.length > MAX_PLAN_LINES}
          onClick={() => onChange(bundle)}
        >
          {MINES.map((mine) => (
            <Icon key={mine} name={mine} />
          ))}
        </button>
      </div>
      <p className={styles.muted}>{t.automation.addHint}</p>
      <button type="button" className={styles.link} onClick={() => onAdding('none')}>
        {t.automation.addClose}
      </button>
    </div>
  );
}
