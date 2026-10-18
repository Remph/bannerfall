import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import type { MapVillage } from '../../api/queries/map';
import { useSaveVillageNote } from '../../api/queries/notes';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { chatLength, CHAT_MAX_LENGTH } from '../../game/chat';
import { formatCoords, formatNumber } from '../../game/format';
import { useI18n } from '../../i18n';
import styles from './VillageNote.module.css';

/**
 * The player's own note in the village panel (1.4, 02-game-rules §23): under the name, cut after
 * two lines; a tap opens the editor. Without a note the panel only has a pencil in its head.
 */
export function NoteBox({ note, onOpen }: { note: string; onOpen: () => void }) {
  const { t } = useI18n();
  return (
    <button type="button" className={styles.box} aria-label={t.map.note.edit(note)} onClick={onOpen}>
      <Icon name="pencil" />
      <span className={styles.text}>{note}</span>
    </button>
  );
}

interface NoteSheetProps {
  open: boolean;
  village: MapVillage;
  onClose: () => void;
}

/**
 * Writing, changing or deleting the note in a sheet over the map, so the map stays where it is.
 * Saving an empty text deletes the note too. Mount it with a fresh `key` per opening: the draft
 * starts from the saved note.
 */
export function NoteSheet({ open, village, onClose }: NoteSheetProps) {
  const { t, language } = useI18n();
  const save = useSaveVillageNote();
  const saved = village.note ?? '';
  const [draft, setDraft] = useState(saved);
  const length = chatLength(draft.trim());
  const tooLong = length > CHAT_MAX_LENGTH;
  const name = `${village.name ?? (village.keep ? t.map.keep : t.map.barbarianVillage)} ${formatCoords(village.x, village.y)}`;

  function store(text: string) {
    save.mutate({ villageId: village.id, text }, { onSuccess: onClose });
  }

  return (
    <Sheet open={open} onClose={onClose} title={t.map.note.title(name)}>
      <form
        className={styles.sheet}
        onSubmit={(event) => {
          event.preventDefault();
          if (!tooLong) {
            store(draft);
          }
        }}
      >
        <label className="sr-only" htmlFor={`note-${village.id}`}>
          {t.map.note.label}
        </label>
        <textarea
          id={`note-${village.id}`}
          className={styles.input}
          value={draft}
          rows={5}
          // the server counts code points: room for 500 emoji, the counter says when it is too long
          maxLength={CHAT_MAX_LENGTH * 2}
          placeholder={t.map.note.placeholder}
          onChange={(event) => {
            setDraft(event.target.value);
            save.reset();
          }}
        />
        <p className={styles.meta}>
          <span>{t.map.note.onlyYou}</span>
          <span className={tooLong ? styles.over : undefined}>
            {t.map.note.count(formatNumber(length, language), formatNumber(CHAT_MAX_LENGTH, language))}
          </span>
        </p>
        {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
        <div className={styles.actions}>
          {saved !== '' && (
            <button type="button" className={styles.delete} disabled={save.isPending} onClick={() => store('')}>
              <Icon name="trash" />
              {t.map.note.delete}
            </button>
          )}
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="submit"
            disabled={tooLong || save.isPending || draft.trim() === saved || (saved === '' && draft.trim() === '')}
          >
            {t.common.save}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
