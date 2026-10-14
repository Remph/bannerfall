import { usePaladin } from '../../api/queries/paladin';
import { useT } from '../../i18n';
import styles from './RecruitPage.module.css';

/**
 * Under the paladin's name in the barracks (M31): a dot for every slot (filled when a paladin takes
 * it), how many are in use and when the next one opens (02-game-rules §5.5).
 */
export function PaladinSlots() {
  const t = useT();
  const paladin = usePaladin();
  if (paladin.data === undefined) {
    return null;
  }
  const { slots, used, villages, nextSlotAtVillages } = paladin.data;
  return (
    <div className={styles.paladin}>
      <p className={styles.paladinSlots}>
        {Array.from({ length: slots }, (_, index) => (
          <span
            key={index}
            className={index < used ? `${styles.slot} ${styles.slotTaken}` : styles.slot}
            aria-hidden="true"
          />
        ))}
        <span>{t.paladin.slots(used, slots)}</span>
      </p>
      <p className={styles.paladinNote}>
        {nextSlotAtVillages === null || nextSlotAtVillages === undefined
          ? t.paladin.atMaximum
          : t.paladin.nextSlot(nextSlotAtVillages, villages)}
      </p>
    </div>
  );
}
