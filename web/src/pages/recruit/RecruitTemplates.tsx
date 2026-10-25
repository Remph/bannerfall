import { useState, type ReactNode } from 'react';
import { errorMessage } from '../../api/errors';
import { useNoble } from '../../api/queries/noble';
import { usePaladin } from '../../api/queries/paladin';
import { useEnqueueRecruitAll, type RecruitBuilding } from '../../api/queries/recruitment';
import {
  useDeleteRecruitTemplate,
  useRecruitTemplates,
  useSaveRecruitTemplate,
} from '../../api/queries/recruitTemplates';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { CostList } from '../../components/CostList';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { IconButton } from '../../components/IconButton';
import { MoreMenu } from '../../components/MoreMenu';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import { UnitCountInput } from '../../components/UnitCountInput';
import { UnitSummary } from '../../components/UnitSummary';
import { nextFreeLetter, type TemplateLetter } from '../../game/farmTemplates';
import { formatNumber } from '../../game/format';
import { freePaladinSlots } from '../../game/paladin';
import { MAX_RECRUIT_COUNT, totalOf } from '../../game/recruitment';
import {
  linePrice,
  ordersOf,
  planSeconds,
  planTemplate,
  type PlanLine,
  type RecruitTemplate,
} from '../../game/recruitTemplates';
import { projectResources } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import type { UnitMap } from '../../game/troops';
import { parseCount } from '../../game/unitCount';
import type { UnitType } from '../../game/gameData';
import { useI18n, useT } from '../../i18n';
import farm from '../farm/Farm.module.css';
import { TemplateLetterBadge } from '../farm/TemplateButtons';
import letterStyles from '../farm/FarmTemplates.module.css';
import styles from './RecruitPage.module.css';
import { loadTemplatesFolded, saveTemplatesFolded } from './templatesFolded';

/** Longest template name (server: RecruitTemplateService.MAX_NAME_LENGTH). */
const MAX_NAME_LENGTH = 16;

interface RecruitTemplatesProps {
  village: Village;
  /** The recruiting buildings the server lists, in UI order. */
  buildings: readonly RecruitBuilding[];
  /** The village's own units away; undefined while the troops load. */
  away: UnitMap | undefined;
  /** Orders per building (world setting); undefined while it loads. */
  queueSize: number | undefined;
}

/**
 * Recruit templates (1.4, 02-game-rules §5.2b) above the building tabs: "Templates" folds the row,
 * one button per template opens its plan for this village, the pencil manages the templates.
 */
export function RecruitTemplates(props: RecruitTemplatesProps) {
  const t = useT();
  const templates = useRecruitTemplates();
  const [folded, setFolded] = useState(loadTemplatesFolded);
  const [opened, setOpened] = useState<TemplateLetter>();
  const [managing, setManaging] = useState(false);
  const list = templates.data ?? [];
  const toggle = () => {
    setFolded(!folded);
    saveTemplatesFolded(!folded);
  };
  return (
    <section className={styles.templates} aria-label={t.recruitTemplates.title}>
      <h2 className={styles.templatesTitle}>
        <button type="button" className={styles.templatesToggle} aria-expanded={!folded} onClick={toggle}>
          <span>{t.recruitTemplates.title}</span>
          {folded && list.length > 0 && (
            <span className={styles.templatesCount}>
              <span aria-hidden="true">{list.length}</span>
              <span className="sr-only">{t.recruitTemplates.count(list.length)}</span>
            </span>
          )}
          <Icon name={folded ? 'chevronDown' : 'chevronUp'} />
        </button>
      </h2>
      {!folded && list.length > 0 && (
        <IconButton
          className={styles.templatesPencil}
          icon="pencil"
          label={t.recruitTemplates.edit}
          onClick={() => setManaging(true)}
        />
      )}
      {!folded && templates.isSuccess && list.length === 0 && (
        <button type="button" className={styles.templatesCreate} onClick={() => setManaging(true)}>
          <Icon name="plus" />
          {t.recruitTemplates.create}
        </button>
      )}
      {!folded && list.length > 0 && (
        <ul className={letterStyles.buttons}>
          {list.map((template) => (
            <li key={template.letter}>
              <button
                type="button"
                className={letterStyles.button}
                aria-label={t.recruitTemplates.open(template.letter, template.name)}
                onClick={() => setOpened(template.letter)}
              >
                <TemplateLetterBadge letter={template.letter} />
                {template.name}
              </button>
            </li>
          ))}
        </ul>
      )}
      {templates.isError && <ErrorMessage>{errorMessage(t, templates.error)}</ErrorMessage>}
      <PlanSheet
        {...props}
        template={list.find((template) => template.letter === opened)}
        onClose={() => setOpened(undefined)}
      />
      <ManageSheet
        open={managing}
        templates={list}
        village={props.village}
        buildings={props.buildings}
        away={props.away}
        onClose={() => setManaging(false)}
      />
    </section>
  );
}

interface PlanSheetProps extends RecruitTemplatesProps {
  template: RecruitTemplate | undefined;
  onClose: () => void;
  /** Another title and hint than a template's (re-recruiting losses, 1.6.3). */
  title?: string;
  hint?: string;
  /** Makes the goal of these units editable; a goal of 0 keeps the row, recruiting nothing. */
  goalEdit?: { units: readonly UnitType[]; onChange: (unit: UnitType, goal: number) => void };
  /** The orders were accepted (before the sheet closes). */
  onRecruited?: () => void;
}

/** A template's plan for this village (goal, have, now per unit) and the one button that recruits it. */
export function PlanSheet(props: PlanSheetProps) {
  const { village, buildings, away, queueSize, template, onClose, goalEdit, onRecruited } = props;
  const t = useT();
  const now = useGameNow();
  const enqueue = useEnqueueRecruitAll(village.id);
  // the paladin's free slots cap his line (M31); only asked for where he is listed
  const paladinListed = buildings.some((building) => building.units.some((unit) => unit.unit === 'PALADIN'));
  const paladin = usePaladin(paladinListed);
  // the noble's price rises with every one (1.6): his line is priced with it
  const nobleListed = buildings.some((building) => building.units.some((unit) => unit.unit === 'NOBLE'));
  const noble = useNoble(nobleListed);
  const stock = projectResources(village, now);
  const freePop = village.population.capacity - village.population.used;
  // without the units away the plan would recruit them again, without the slots too many paladins
  // (a failed slot query plans without the cap: the server still refuses a paladin too many)
  const slotsLoading = paladinListed && paladin.data === undefined && !paladin.isError;
  const priceLoading = nobleListed && noble.data === undefined && !noble.isError;
  const ready = away !== undefined && queueSize !== undefined && !slotsLoading && !priceLoading;
  const lines =
    template === undefined || !ready
      ? []
      : planTemplate({
          goal: template.units,
          buildings,
          home: village.unitsHome,
          away,
          stock,
          freePop,
          queueSize,
          freeSlots: paladin.data === undefined ? undefined : freePaladinSlots(paladin.data),
          noble: noble.data,
        });
  const orders = ordersOf(lines);
  const recruiting = lines.filter((line) => line.now > 0);
  const total = totalOf(
    recruiting.map((line) => ({
      cost: linePrice({ noble: noble.data }, line.unit, line.now),
      pop: line.unit.pop,
      count: line.now,
    })),
  );
  const allReached = lines.length > 0 && lines.every((line) => line.skip === 'reached');
  const close = () => {
    enqueue.reset();
    onClose();
  };
  return (
    <Sheet
      open={template !== undefined}
      onClose={close}
      title={props.title ?? (template === undefined ? '' : `${template.letter} · ${template.name}`)}
    >
      <div className={farm.body}>
        <p className={farm.hint}>{props.hint ?? t.recruitTemplates.planHint}</p>
        {!ready && <p className={farm.hint}>{t.recruitTemplates.loading}</p>}
        {(lines.length > 0 || (goalEdit !== undefined && ready)) && (
          <table className={styles.planTable}>
            <thead>
              <tr>
                <th scope="col">
                  <span className="sr-only">{t.recruitTemplates.unit}</span>
                </th>
                <th scope="col">{t.recruitTemplates.goal}</th>
                <th scope="col">{t.recruitTemplates.have}</th>
                <th scope="col">{t.recruitTemplates.now}</th>
              </tr>
            </thead>
            <tbody>
              {goalEdit === undefined
                ? lines.map((line) => <PlanRow key={line.unit.unit} line={line} />)
                : goalEdit.units.map((type) => {
                    const line = lines.find((candidate) => candidate.unit.unit === type);
                    const goalCell = (
                      <GoalInput
                        unit={type}
                        value={template?.units[type] ?? 0}
                        onChange={(goal) => goalEdit.onChange(type, goal)}
                      />
                    );
                    return line === undefined ? (
                      <PlanGoalRow key={type} unit={type} goalCell={goalCell} />
                    ) : (
                      <PlanRow key={type} line={line} goalCell={goalCell} />
                    );
                  })}
            </tbody>
          </table>
        )}
        {allReached && <p className={farm.hint}>{t.recruitTemplates.allReached}</p>}
        {/* only where resources or population are what's missing: skipped units say their own reason */}
        {orders.length === 0 && lines.some((line) => line.skip === null) && (
          <p className={farm.hint}>{t.recruitTemplates.tooPoor}</p>
        )}
        {orders.length > 0 && (
          <CostList
            label={t.recruitTemplates.total}
            timeLabel={t.recruit.time}
            showLabel
            cost={total.cost}
            pop={total.pop}
            seconds={planSeconds(lines)}
            stock={stock}
            freePop={freePop}
          />
        )}
        {enqueue.isError && <ErrorMessage>{errorMessage(t, enqueue.error)}</ErrorMessage>}
        <div className={farm.actions}>
          <Button type="button" variant="secondary" onClick={close}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={orders.length === 0 || enqueue.isPending}
            onClick={() =>
              enqueue.mutate(orders, {
                onSuccess: () => {
                  onRecruited?.();
                  close();
                },
              })
            }
          >
            {enqueue.isPending ? t.recruit.recruiting : t.recruitTemplates.recruit(orders.length)}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

/** One unit of the plan: name, goal, what the village has and what it recruits now (or why not). */
function PlanRow({ line, goalCell }: { line: PlanLine; goalCell?: ReactNode }) {
  const { t, language } = useI18n();
  return (
    <tr className={line.now > 0 ? undefined : styles.planSkipped}>
      <th scope="row">
        <span className={styles.planUnit}>
          <span className={farm.unitIcon}>
            <Icon name={line.unit.unit} />
          </span>
          {t.units[line.unit.unit]}
        </span>
      </th>
      <td>{goalCell ?? formatNumber(line.goal, language)}</td>
      <td>{formatNumber(line.have, language)}</td>
      <td className={styles.planNow}>
        {line.skip === null ? formatNumber(line.now, language) : t.recruitTemplates.skip[line.skip]}
      </td>
    </tr>
  );
}

/** An editable plan's unit with a goal of 0: nothing to recruit (the goal can be raised again). */
function PlanGoalRow({ unit, goalCell }: { unit: UnitType; goalCell: ReactNode }) {
  const t = useT();
  return (
    <tr className={styles.planSkipped}>
      <th scope="row">
        <span className={styles.planUnit}>
          <span className={farm.unitIcon}>
            <Icon name={unit} />
          </span>
          {t.units[unit]}
        </span>
      </th>
      <td>{goalCell}</td>
      <td aria-hidden="true">·</td>
      <td className={styles.planNow}>·</td>
    </tr>
  );
}

/** The goal of one unit as a small number field (the plan of lost units, 1.6.3). */
function GoalInput({ unit, value, onChange }: { unit: UnitType; value: number; onChange: (goal: number) => void }) {
  const t = useT();
  return (
    <input
      className={styles.goalInput}
      type="text"
      inputMode="numeric"
      pattern="[0-9]*"
      autoComplete="off"
      aria-label={t.recruitLosses.goalLabel(t.units[unit])}
      value={String(value)}
      onChange={(event) => onChange(parseCount(event.target.value, MAX_RECRUIT_COUNT))}
    />
  );
}

interface ManageSheetProps {
  open: boolean;
  templates: readonly RecruitTemplate[];
  village: Village;
  buildings: readonly RecruitBuilding[];
  away: UnitMap | undefined;
  onClose: () => void;
}

/** The templates with "…" (change, delete) and the next free letter to create; editing replaces the list. */
function ManageSheet({ open, templates, village, buildings, away, onClose }: ManageSheetProps) {
  const t = useT();
  const remove = useDeleteRecruitTemplate();
  const [editing, setEditing] = useState<TemplateLetter>();
  const nextFree = nextFreeLetter(templates);
  if (open && editing !== undefined) {
    return (
      <EditSheet
        // a fresh form for each letter, from its saved name and units
        key={editing}
        letter={editing}
        template={templates.find((template) => template.letter === editing)}
        village={village}
        buildings={buildings}
        away={away}
        onClose={() => setEditing(undefined)}
      />
    );
  }
  return (
    <Sheet open={open} onClose={onClose} title={t.recruitTemplates.manageTitle}>
      <div className={farm.body}>
        <p className={farm.hint}>{t.recruitTemplates.manageHint}</p>
      </div>
      {templates.length > 0 && (
        <ul className={farm.list}>
          {templates.map((template) => (
            <li key={template.letter}>
              <div className={farm.templateRow}>
                <TemplateLetterBadge letter={template.letter} />
                <div className={farm.templateFacts}>
                  <span>{template.name}</span>
                  <UnitSummary units={template.units} />
                </div>
                <MoreMenu
                  label={t.recruitTemplates.more(template.letter)}
                  items={[
                    { label: t.recruitTemplates.change, icon: 'pencil', onSelect: () => setEditing(template.letter) },
                    {
                      label: t.recruitTemplates.remove,
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
        <div className={farm.body}>
          {remove.isError && <ErrorMessage>{errorMessage(t, remove.error)}</ErrorMessage>}
          {nextFree !== undefined && (
            <button type="button" className={farm.newTemplate} onClick={() => setEditing(nextFree)}>
              <Icon name="plus" />
              {t.recruitTemplates.newTemplate(nextFree)}
            </button>
          )}
        </div>
      )}
    </Sheet>
  );
}

interface EditSheetProps {
  letter: TemplateLetter;
  template: RecruitTemplate | undefined;
  village: Village;
  buildings: readonly RecruitBuilding[];
  away: UnitMap | undefined;
  onClose: () => void;
}

/**
 * A template's name and goal army: one count per unit the world has (the units the recruit screen
 * lists); "take this village's army" fills the counts with the village's own units.
 */
function EditSheet({ letter, template, village, buildings, away, onClose }: EditSheetProps) {
  const t = useT();
  const save = useSaveRecruitTemplate();
  const [name, setName] = useState(template?.name ?? '');
  const [counts, setCounts] = useState<UnitMap>(template?.units ?? {});
  const types = buildings.flatMap((building) => building.units.map((unit) => unit.unit));
  const own = (type: (typeof types)[number]) => (village.unitsHome[type] ?? 0) + (away?.[type] ?? 0);
  const filled = types.some((type) => (counts[type] ?? 0) > 0);
  const trimmed = name.trim();
  return (
    <Sheet open onClose={onClose} title={t.recruitTemplates.editTitle(letter)}>
      <div className={farm.body}>
        <TextField
          label={t.recruitTemplates.name}
          hint={t.recruitTemplates.nameHint}
          value={name}
          maxLength={MAX_NAME_LENGTH}
          onChange={(event) => {
            setName(event.target.value);
            save.reset();
          }}
        />
        <div className={styles.editHead}>
          <span className={styles.editLabel}>{t.recruitTemplates.goalLabel}</span>
          <button
            type="button"
            className={farm.newTemplate}
            onClick={() => {
              setCounts(Object.fromEntries(types.map((type) => [type, Math.min(own(type), MAX_RECRUIT_COUNT)])));
              save.reset();
            }}
          >
            <Icon name="troops" />
            {t.recruitTemplates.fromVillage}
          </button>
        </div>
        <ul className={farm.pickList}>
          {types.map((type) => (
            <li key={type} className={farm.pickRow}>
              <span className={farm.pickName}>
                <span className={farm.unitIcon}>
                  <Icon name={type} />
                </span>
                {t.units[type]}
              </span>
              <span className={farm.pickInput}>
                <UnitCountInput
                  value={counts[type] ?? 0}
                  onChange={(count) => {
                    setCounts((current) => ({ ...current, [type]: count }));
                    save.reset();
                  }}
                  max={Math.min(own(type), MAX_RECRUIT_COUNT)}
                  // the goal is usually more than the village has: + goes on past it
                  stepMax={MAX_RECRUIT_COUNT}
                  limit={MAX_RECRUIT_COUNT}
                  label={t.recruitTemplates.countLabel(t.units[type])}
                  maxLabel={t.recruitTemplates.haveAmount}
                />
              </span>
            </li>
          ))}
        </ul>
        {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
        <div className={farm.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={!filled || trimmed === '' || save.isPending}
            onClick={() =>
              save.mutate(
                // units the world doesn't list here (none, usually) keep their goal
                { letter, name: trimmed, units: counts },
                { onSuccess: onClose },
              )
            }
          >
            {t.recruitTemplates.save}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
