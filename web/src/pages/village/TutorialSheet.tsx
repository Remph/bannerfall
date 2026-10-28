import type { TutorialStepLine } from '../../api/queries/tutorial';
import { Sheet } from '../../components/Sheet';
import { splitLines } from '../../game/tutorial';
import { useT } from '../../i18n';
import styles from './TutorialSheet.module.css';

interface TutorialSheetProps {
  open: boolean;
  onClose: () => void;
  lines: TutorialStepLine[];
  /** The key the card shows now: the one line marked "Next"; other open lines say "Open". */
  currentKey: string | null;
}

/** "All steps": every step and lesson of the world with where it stands (1.7). */
export function TutorialSheet({ open, onClose, lines, currentKey }: TutorialSheetProps) {
  const t = useT();
  const { steps, lessons } = splitLines(lines);
  return (
    <Sheet open={open} onClose={onClose} title={t.tutorial.sheetTitle}>
      <Group title={t.tutorial.stepsHeading} lines={steps} currentKey={currentKey} />
      {lessons.length > 0 && <Group title={t.tutorial.lessonsHeading} lines={lessons} currentKey={currentKey} />}
    </Sheet>
  );
}

function Group({ title, lines, currentKey }: { title: string; lines: TutorialStepLine[]; currentKey: string | null }) {
  const t = useT();
  return (
    <section>
      <h3 className={styles.heading}>{title}</h3>
      <ul className={styles.list}>
        {lines.map((line) => (
          <li key={line.key} className={styles.row} data-status={line.status} data-current={line.key === currentKey}>
            <span className={styles.name}>{t.tutorial.steps[line.key].title}</span>
            <span className={styles.status}>
              {line.status === 'OPEN' && line.key === currentKey ? t.tutorial.next : t.tutorial.status[line.status]}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
