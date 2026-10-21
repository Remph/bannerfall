import type { ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import type { LineState, PlanLine } from '../../game/buildPlans';
import type { RecruitTemplate } from '../../game/recruitTemplates';
import { useT } from '../../i18n';
import styles from './AutomationPage.module.css';

const stateClass: Record<LineState, string | undefined> = {
  done: styles.done,
  current: styles.current,
  next: undefined,
};

interface PlanLineViewProps {
  line: PlanLine;
  index: number;
  /** How the line stands in the village (the page); the editor leaves it out. */
  state?: LineState;
  templates: readonly RecruitTemplate[];
  /** The editor's buttons on the right, and a problem below. */
  children?: ReactNode;
}

/** One line of a plan: its number (a tick once reached), the building and level or the recruit line. */
export function PlanLineView({ line, index, state = 'next', templates, children }: PlanLineViewProps) {
  const classes = [
    styles.line,
    stateClass[state],
    line.kind === 'recruit' && state === 'next' ? styles.recruitLine : undefined,
  ];
  return (
    <li className={classes.filter(Boolean).join(' ')} aria-current={state === 'current' ? 'step' : undefined}>
      <span className={styles.lineNo}>
        {state === 'done' && <Icon name="check" />}
        <span className={state === 'done' ? 'sr-only' : undefined}>{index + 1}</span>
      </span>
      <LineContent line={line} templates={templates} />
      {children}
    </li>
  );
}

function LineContent({ line, templates }: { line: PlanLine; templates: readonly RecruitTemplate[] }) {
  const t = useT();
  if (line.kind === 'recruit') {
    const template = templates.find((candidate) => candidate.letter === line.template);
    return (
      <>
        <span className={styles.lineIcon}>
          <Icon name="troops" />
        </span>
        <span className={styles.lineName}>{t.automation.startRecruiting}</span>
        <span className={styles.chip}>{t.automation.recruitChip(line.template, template?.name ?? '–')}</span>
      </>
    );
  }
  return (
    <>
      <span className={styles.lineIcon}>
        <Icon name={line.building} />
      </span>
      <span className={styles.lineName}>{t.buildings[line.building]}</span>
      <span className={styles.badge}>{t.village.level(line.level)}</span>
    </>
  );
}
