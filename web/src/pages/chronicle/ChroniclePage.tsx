import { useCallback, useRef, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { type Chronicle, type ChronicleEntry, useChronicle } from '../../api/queries/chronicle';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { entriesByDay, entryIcon, entryText, entryVillageIndex, frameForDay } from '../../game/chronicle';
import { formatDateTime, formatTime } from '../../game/format';
import { prefersReducedMotion } from '../../game/motion';
import { useI18n } from '../../i18n';
import { ActionsStayOn } from '../../layout/ActionsStayOn';
import { rankingPath } from '../ranking/rankingPaths';
import styles from './ChroniclePage.module.css';
import { ReplayMap } from './ReplayMap';

/**
 * /chronicle – the story of an ended world (2.1 I1, 02-game-rules §32): the replay of who owned what
 * and the turning points, day by day. Reached from the world-end screen and the result above the
 * ranking; it stays open as long as the world does.
 */
export function ChroniclePage() {
  const { t } = useI18n();
  const chronicle = useChronicle();
  return (
    <div className={styles.page}>
      <BackLink to={rankingPath(0)}>{t.ranking.title}</BackLink>
      <header className={styles.head}>
        <h1>{t.chronicle.title}</h1>
        {chronicle.data !== undefined && <Intro chronicle={chronicle.data} />}
      </header>
      {chronicle.isError ? (
        <>
          <ErrorMessage>{errorMessage(t, chronicle.error)}</ErrorMessage>
          <ActionsStayOn>
            <div>
              <Button type="button" onClick={() => void chronicle.refetch()}>
                {t.common.retry}
              </Button>
            </div>
          </ActionsStayOn>
        </>
      ) : chronicle.isPending ? (
        <p>{t.common.loading}</p>
      ) : (
        <Story chronicle={chronicle.data} />
      )}
    </div>
  );
}

function Intro({ chronicle }: { chronicle: Chronicle }) {
  const { t, language } = useI18n();
  return (
    <p className={styles.meta}>
      {t.chronicle.intro(chronicle.days, formatDateTime(Date.parse(chronicle.endedAt), language))}
    </p>
  );
}

/** The replay and the timeline; picking a moment shows its day (and its village) on the map. */
export function Story({ chronicle }: { chronicle: Chronicle }) {
  const { t } = useI18n();
  const [frame, setFrame] = useState(() => Math.max(0, chronicle.map.frames.length - 1));
  const [picked, setPicked] = useState<ChronicleEntry | null>(null);
  const onFrame = useCallback((next: number) => {
    setFrame(next);
    setPicked(null);
  }, []);
  const highlight = picked === null ? null : entryVillageIndex(chronicle, picked);
  const mapRef = useRef<HTMLDivElement>(null);
  return (
    <div className={styles.columns}>
      <div ref={mapRef} className={styles.mapColumn}>
        <Card title={t.chronicle.replay}>
          <ReplayMap chronicle={chronicle} frame={frame} onFrame={onFrame} highlight={highlight} />
        </Card>
      </div>
      <Card title={t.chronicle.timeline}>
        {chronicle.entries.length === 0 ? (
          <p className={styles.meta}>{t.chronicle.empty}</p>
        ) : (
          <>
            <p className={styles.hint}>{t.chronicle.timelineHint}</p>
            <Timeline
              entries={chronicle.entries}
              picked={picked}
              onPick={(entry) => {
                setFrame(frameForDay(chronicle.map, entry.day));
                setPicked(entry);
                showMap(mapRef.current);
              }}
            />
          </>
        )}
      </Card>
    </div>
  );
}

/**
 * On a phone the map lies above the timeline: a moment picked further down would change a map out of
 * sight, so the page scrolls back to it (under the top bar, by the column's scroll margin). In the
 * two columns of a PC the map stays in view and nothing moves. A window scroll, not
 * `scrollIntoView`, which can shift a phone layout sideways.
 */
function showMap(column: HTMLElement | null) {
  if (column === null) {
    return;
  }
  const margin = Number.parseFloat(getComputedStyle(column).scrollMarginTop) || 0;
  const box = column.getBoundingClientRect();
  if (box.top >= margin - 1 && box.top < window.innerHeight / 2) {
    return; // in view (the PC's sticky column, or a phone already at the map)
  }
  window.scrollTo({
    top: window.scrollY + box.top - margin,
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
  });
}

interface TimelineProps {
  entries: readonly ChronicleEntry[];
  picked: ChronicleEntry | null;
  onPick: (entry: ChronicleEntry) => void;
}

/** The turning points under a heading per day; each one is a button that shows its day on the map. */
export function Timeline({ entries, picked, onPick }: TimelineProps) {
  const { t, language } = useI18n();
  return (
    <ol className={styles.days}>
      {entriesByDay(entries).map(({ day, entries: ofDay }) => (
        <li key={day} className={styles.day}>
          <h3 className={styles.dayTitle}>{day === 0 ? t.chronicle.beforeOpening : t.chronicle.dayName(day)}</h3>
          <ol className={styles.entries}>
            {ofDay.map((entry, index) => {
              const text = entryText(t, entry);
              const classes = [
                styles.entry,
                entry.mine && styles.mine,
                entry === picked && styles.picked,
                (entry.kind === 'WORLD_ENDED' || entry.kind === 'VICTORY_CLOCK') && styles.big,
              ]
                .filter(Boolean)
                .join(' ');
              return (
                <li key={`${entry.at}-${index}`}>
                  <button
                    type="button"
                    className={classes}
                    aria-pressed={entry === picked}
                    aria-label={`${text.line} ${text.notes.join(' ')} – ${t.chronicle.showDay(t.chronicle.dayName(day))}`}
                    onClick={() => onPick(entry)}
                  >
                    <span className={styles.sign} aria-hidden="true">
                      <Icon name={entryIcon(entry.kind)} />
                    </span>
                    <span className={styles.text}>
                      <span className={styles.line}>{text.line}</span>
                      {text.notes.map((note) => (
                        <span key={note} className={styles.note}>
                          {note}
                        </span>
                      ))}
                    </span>
                    <time className={styles.time} dateTime={entry.at}>
                      {formatTime(Date.parse(entry.at), language)}
                    </time>
                  </button>
                </li>
              );
            })}
          </ol>
        </li>
      ))}
    </ol>
  );
}
