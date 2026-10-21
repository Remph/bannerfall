import { useId, useState } from 'react';
import { Navigate, useParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import {
  useBuildPlans,
  usePlanBuildings,
  useSetVillageAutomation,
  useVillageAutomation,
  type VillageAutomation,
} from '../../api/queries/automation';
import { useRecruitTemplates } from '../../api/queries/recruitTemplates';
import { useVillage } from '../../api/queries/villages';
import { BackLink } from '../../components/BackLink';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { lineState, type BuildPlan } from '../../game/buildPlans';
import type { TemplateLetter } from '../../game/farmTemplates';
import type { RecruitTemplate } from '../../game/recruitTemplates';
import { useGameNow } from '../../game/serverTime';
import { useI18n, useT } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { VillageError } from '../village/VillageError';
import styles from './AutomationPage.module.css';
import { byHandText, missingText, statusText } from './automationText';
import { PlanLineView } from './PlanLineView';
import { PlanEditor, PlanPicker } from './PlanSheets';

/**
 * /v/:villageId/automation – the village automation (02-game-rules §25, 1.6): the switch and the
 * plan of this village, what it does now, and the whole plan with the lines already reached.
 */
export function AutomationPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  return <Automation key={villageId} villageId={villageId} />;
}

/** The sheet that is open: the picker, or the editor of a plan (`isNew` from the picker). */
type Opened = { kind: 'pick' } | { kind: 'edit'; letter: TemplateLetter; isNew: boolean } | null;

function Automation({ villageId }: { villageId: number }) {
  const t = useT();
  const village = useVillage(villageId);
  const status = useVillageAutomation(villageId);
  const switchStateId = useId();
  const plans = useBuildPlans();
  const buildings = usePlanBuildings();
  const templates = useRecruitTemplates();
  const set = useSetVillageAutomation(villageId);
  const [opened, setOpened] = useState<Opened>(null);

  const failed = [village, status, plans, buildings].find((query) => query.isError);
  if (failed !== undefined) {
    return <VillageError error={failed.error} onRetry={() => void failed.refetch()} />;
  }
  if (!village.isSuccess || !status.isSuccess || !plans.isSuccess || !buildings.isSuccess) {
    return <p>{t.common.loading}</p>;
  }

  const plan = plans.data.find((candidate) => candidate.letter === status.data.plan);
  const pick = (letter: TemplateLetter | null) =>
    set.mutate({ plan: letter, enabled: letter !== null && (status.data.plan === null || status.data.enabled) });
  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <BackLink to={`/v/${villageId}/hq`}>{t.automation.back}</BackLink>
        <h1>{t.automation.title}</h1>
        <p className={styles.muted}>{village.data.name}</p>
      </header>
      {status.data.state === 'LOCKED' ? (
        <LockedCard status={status.data} />
      ) : (
        <div className={styles.layout}>
          <Card
            title={t.automation.villageTitle}
            action={
              // 1.6 UI review: the switch says what it does, and waits for a plan ("Plan wählen" starts it)
              status.data.plan === null ? undefined : (
                <label className={styles.switchRow}>
                  <span id={switchStateId} className={styles.switchState}>
                    {status.data.enabled ? t.automation.running : t.automation.paused}
                  </span>
                  <input
                    type="checkbox"
                    role="switch"
                    aria-label={t.automation.switchLabel}
                    aria-describedby={switchStateId}
                    className={styles.switch}
                    checked={status.data.enabled}
                    disabled={set.isPending}
                    onChange={(event) => set.mutate({ plan: status.data.plan, enabled: event.target.checked })}
                  />
                </label>
              )
            }
          >
            <div className={styles.body}>
              <PlanButton plan={plan} onClick={() => setOpened({ kind: 'pick' })} />
              <StatusLines status={status.data} templates={templates.data ?? []} />
              {plan !== undefined && <Progress status={status.data} />}
              {set.isError && <ErrorMessage>{errorMessage(t, set.error)}</ErrorMessage>}
            </div>
          </Card>
          {plan === undefined ? (
            <Card title={t.automation.plansTitle}>
              <p className={`${styles.body} ${styles.muted}`}>{t.automation.plansHint}</p>
            </Card>
          ) : (
            <PlanCard
              plan={plan}
              line={status.data.line}
              templates={templates.data ?? []}
              onEdit={() => setOpened({ kind: 'edit', letter: plan.letter, isNew: false })}
            />
          )}
        </div>
      )}
      <PlanPicker
        open={opened?.kind === 'pick'}
        village={village.data}
        plans={plans.data}
        current={status.data.plan}
        onPick={(letter) => {
          pick(letter);
          setOpened(null);
        }}
        onCreate={(letter) => setOpened({ kind: 'edit', letter, isNew: true })}
        // the closing picker reports its close after the editor has opened: keep the editor
        onClose={() => setOpened((current) => (current?.kind === 'pick' ? null : current))}
      />
      {opened?.kind === 'edit' && (
        <PlanEditor
          letter={opened.letter}
          plan={plans.data.find((candidate) => candidate.letter === opened.letter)}
          village={village.data}
          buildings={buildings.data}
          templates={templates.data ?? []}
          onSaved={() => {
            // a plan made from the picker becomes this village's plan
            if (opened.isNew) {
              pick(opened.letter);
            }
          }}
          onClose={() => setOpened(null)}
        />
      )}
    </div>
  );
}

function LockedCard({ status }: { status: VillageAutomation }) {
  const t = useT();
  return (
    <Card title={t.automation.villageTitle} action={<Icon name="lock" className={styles.inlineIcon} />}>
      <div className={styles.body}>
        {status.villagesNeeded === 0 ? (
          <p className={styles.muted}>{t.automation.noWorld}</p>
        ) : (
          <>
            <p className={styles.status}>{t.automation.locked(status.villagesNeeded)}</p>
            <p className={styles.muted}>{t.automation.lockedCount(status.villages, status.villagesNeeded)}</p>
          </>
        )}
      </div>
    </Card>
  );
}

function PlanButton({ plan, onClick }: { plan: BuildPlan | undefined; onClick: () => void }) {
  const t = useT();
  if (plan === undefined) {
    return (
      <button type="button" className={styles.planButton} onClick={onClick}>
        <span className={styles.letterEmpty} aria-hidden="true">
          <Icon name="plus" />
        </span>
        <span className={styles.planName}>{t.automation.choosePlan}</span>
      </button>
    );
  }
  return (
    <button
      type="button"
      className={styles.planButton}
      onClick={onClick}
      aria-label={t.automation.changePlan(plan.letter, plan.name)}
    >
      <span className={styles.letter} aria-hidden="true">
        {plan.letter}
      </span>
      <span className={styles.planName}>{plan.name}</span>
      <Icon name="chevronDown" className={styles.inlineIcon} />
    </button>
  );
}

/** What the automation does now, and from the recruit line on what the template still lacks. */
function StatusLines({ status, templates }: { status: VillageAutomation; templates: readonly RecruitTemplate[] }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const text = statusText(t, language, now, status);
  const template = templates.find((candidate) => candidate.letter === status.recruiting);
  return (
    <>
      {text !== undefined && (
        <p className={styles.status}>
          <Icon name="clock" className={styles.inlineIcon} />
          <span>{text}</span>
        </p>
      )}
      {status.recruiting !== null && (
        <p className={styles.status}>
          <Icon name="troops" className={styles.inlineIcon} />
          <span>
            {template === undefined ? (
              t.automation.recruitingGone(status.recruiting)
            ) : (
              <>
                {t.automation.recruiting(template.letter, template.name)}{' '}
                {Object.keys(status.missing).length === 0
                  ? t.automation.templateFull
                  : t.automation.missing(missingText(t, language, status.missing))}
              </>
            )}
          </span>
        </p>
      )}
      {status.recruiting !== null && status.byHand.length > 0 && (
        <p className={styles.muted}>{t.automation.byHand(byHandText(t, status.byHand))}</p>
      )}
    </>
  );
}

function Progress({ status }: { status: VillageAutomation }) {
  const t = useT();
  const count = Math.max(1, status.lineCount);
  const shown = Math.min(status.line + 1, status.lineCount);
  return (
    <div className={styles.progress}>
      <span className={styles.bar} aria-hidden="true">
        <span className={styles.fill} style={{ transform: `scaleX(${Math.min(1, status.line / count)})` }} />
      </span>
      <span className={styles.muted}>{t.automation.progress(shown, status.lineCount)}</span>
    </div>
  );
}

interface PlanCardProps {
  plan: BuildPlan;
  /** The status's current line. */
  line: number;
  templates: readonly RecruitTemplate[];
  onEdit: () => void;
}

/** The whole plan: reached lines ticked, the current one marked. */
function PlanCard({ plan, line, templates, onEdit }: PlanCardProps) {
  const t = useT();
  return (
    <Card
      title={t.automation.planTitle(plan.letter, plan.name)}
      action={
        <button type="button" className={styles.link} onClick={onEdit}>
          <Icon name="pencil" /> {t.automation.edit}
        </button>
      }
    >
      <ol className={styles.lines}>
        {plan.lines.map((planLine, index) => (
          <PlanLineView
            // lines are a list without ids; they only change in the editor
            key={index}
            line={planLine}
            index={index}
            state={lineState(index, line)}
            templates={templates}
          />
        ))}
      </ol>
    </Card>
  );
}
