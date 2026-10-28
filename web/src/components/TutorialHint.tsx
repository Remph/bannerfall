import { useTutorial, type TutorialStepKey } from '../api/queries/tutorial';
import { useT } from '../i18n';
import styles from './TutorialHint.module.css';

/**
 * The tutorial's current step as one quiet line on the page it is about (1.7), so the hint is
 * still there where the player acts. Nothing unless that step is the current one.
 */
export function TutorialHint({ steps }: { steps: TutorialStepKey[] }) {
  const t = useT();
  const current = useTutorial().data?.current;
  if (current == null || current.kind !== 'STEP' || !steps.includes(current.key)) {
    return null;
  }
  const text = t.tutorial.steps[current.key];
  return (
    <p className={styles.hint}>
      <strong>{text.title}.</strong> {text.text}
    </p>
  );
}
