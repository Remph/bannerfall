import { useState, type SubmitEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useSaveVillageTag, type Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import { cleanVillageTag, VILLAGE_TAG_MAX_LENGTH } from '../../game/villageTags';
import { useT } from '../../i18n';
import styles from './VillageTagSheet.module.css';

/**
 * Setting, changing or removing the player's own tag on the village (1.7, 02-game-rules §26) in a
 * sheet. The field keeps capitals and digits only; saving an empty field removes the tag too. Mount
 * it with a fresh `key` per opening: the draft starts from the saved tag.
 */
export function VillageTagSheet({
  open,
  village,
  tag,
  onClose,
}: {
  open: boolean;
  village: Village;
  tag: string | null;
  onClose: () => void;
}) {
  const t = useT();
  const save = useSaveVillageTag(village.id);
  const saved = tag ?? '';
  const [draft, setDraft] = useState(saved);

  function store(value: string) {
    save.mutate(value, { onSuccess: onClose });
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    store(draft);
  }

  return (
    <Sheet open={open} onClose={onClose} title={t.village.tagTitle(village.name)}>
      <form className={styles.form} onSubmit={handleSubmit} noValidate>
        {save.isError && <ErrorMessage>{errorMessage(t, save.error)}</ErrorMessage>}
        <TextField
          label={t.village.tagLabel}
          hint={t.village.tagHint(VILLAGE_TAG_MAX_LENGTH)}
          value={draft}
          onChange={(event) => {
            setDraft(cleanVillageTag(event.target.value));
            save.reset();
          }}
          placeholder={t.village.tagPlaceholder}
          autoComplete="off"
          autoCapitalize="characters"
          autoCorrect="off"
          spellCheck={false}
          enterKeyHint="done"
          autoFocus
        />
        <div className={styles.actions}>
          {saved !== '' && (
            <button type="button" className={styles.remove} disabled={save.isPending} onClick={() => store('')}>
              <Icon name="trash" />
              {t.village.tagRemove}
            </button>
          )}
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button type="submit" disabled={save.isPending || draft === saved}>
            {t.common.save}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}
