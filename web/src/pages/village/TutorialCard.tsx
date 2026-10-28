import { useState } from 'react';
import { useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useTutorial, useTutorialDone, useTutorialSkip, useTutorialSwitch } from '../../api/queries/tutorial';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { MoreMenu } from '../../components/MoreMenu';
import { resourceText, tutorialPath } from '../../game/tutorial';
import { useI18n } from '../../i18n';
import { TutorialSheet } from './TutorialSheet';
import styles from './TutorialCard.module.css';

/**
 * The tutorial's "next step" card at the top of the village page (1.7, 02-game-rules §27): one
 * step or lesson with its text, one lead button, and Skip behind a "…". Nothing for a player who
 * has the tutorial off or nothing left to learn.
 */
export function TutorialCard({ villageId }: { villageId: number }) {
  const { t, language } = useI18n();
  const navigate = useNavigate();
  const tutorial = useTutorial();
  const done = useTutorialDone();
  const skip = useTutorialSkip();
  const stop = useTutorialSwitch();
  const [listOpen, setListOpen] = useState(false);

  const data = tutorial.data;
  const current = data?.current;
  if (data === undefined || !data.active || current == null) {
    return null;
  }
  const text = t.tutorial.steps[current.key];
  const lesson = current.kind === 'LESSON';
  const show = () => navigate(tutorialPath(current.key, villageId));
  const error = done.error ?? skip.error ?? stop.error;
  const items = [
    {
      label: lesson ? t.tutorial.skipLesson : t.tutorial.skipStep,
      icon: 'chevronRight' as const,
      onSelect: () => skip.mutate(current.key),
    },
    { label: t.tutorial.skipTutorial, icon: 'close' as const, onSelect: () => stop.mutate(false) },
    { label: t.tutorial.allSteps, icon: 'overview' as const, onSelect: () => setListOpen(true) },
  ];

  return (
    <>
      <Card title={text.title} className={styles.card} action={<MoreMenu label={t.tutorial.moreLabel} items={items} />}>
        <div className={styles.body}>
          <p className={styles.tag}>
            {lesson ? t.tutorial.lessonTag : t.tutorial.stepOf(current.index, current.total)}
          </p>
          <p className={styles.text}>{text.text}</p>
          {current.refund != null && (
            <p className={styles.refund}>{t.tutorial.refundWhenDone(resourceText(current.refund, t, language))}</p>
          )}
          {error != null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
          <div className={styles.actions}>
            {lesson ? (
              <>
                <Button onClick={() => done.mutate(current.key)} disabled={done.isPending}>
                  {t.tutorial.gotIt}
                </Button>
                <Button
                  variant="secondary"
                  onClick={() => done.mutate(current.key, { onSuccess: show })}
                  disabled={done.isPending}
                >
                  {t.tutorial.showMe}
                </Button>
              </>
            ) : (
              <Button onClick={show}>{t.tutorial.showMe}</Button>
            )}
          </div>
        </div>
      </Card>
      <TutorialSheet
        open={listOpen}
        onClose={() => setListOpen(false)}
        lines={data.steps}
        currentKey={data.current?.key ?? null}
      />
    </>
  );
}
