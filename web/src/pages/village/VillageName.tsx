import { useState, type KeyboardEvent, type SubmitEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useRenameVillage, type Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import { validateVillageName } from './validation';
import styles from './VillagePage.module.css';

/** The village name as page title; the pencil turns it into a small rename form. */
export function VillageName({ village }: { village: Village }) {
  const t = useT();
  const rename = useRenameVillage(village.id);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  // Show validation errors only after the first save attempt, not while typing.
  const [submitted, setSubmitted] = useState(false);
  const problem = validateVillageName(draft);

  function startEditing() {
    setDraft(village.name);
    setSubmitted(false);
    rename.reset(); // forget an error from an earlier attempt
    setEditing(true);
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (problem !== null) {
      return;
    }
    const name = draft.trim();
    if (name === village.name) {
      setEditing(false);
      return;
    }
    rename.mutate(name, { onSuccess: () => setEditing(false) });
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      setEditing(false);
    }
  }

  if (!editing) {
    return (
      <div className={styles.nameRow}>
        <h1 className={styles.name}>{village.name}</h1>
        <button
          type="button"
          className={styles.iconButton}
          onClick={startEditing}
          aria-label={t.village.rename}
          title={t.village.rename}
        >
          <Icon name="pencil" />
        </button>
      </div>
    );
  }

  const fieldError =
    submitted && problem !== null ? (problem === 'EMPTY' ? t.village.nameEmpty : t.village.nameTooLong) : undefined;

  return (
    <form className={styles.renameForm} onSubmit={handleSubmit} noValidate>
      {rename.isError && <ErrorMessage>{errorMessage(t, rename.error)}</ErrorMessage>}
      <TextField
        label={t.village.nameLabel}
        hint={t.village.nameHint}
        error={fieldError}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={handleKeyDown}
        autoComplete="off"
        enterKeyHint="done"
        autoFocus
      />
      <div className={styles.renameActions}>
        <Button type="submit" disabled={rename.isPending}>
          {t.common.save}
        </Button>
        <Button type="button" variant="secondary" onClick={() => setEditing(false)}>
          {t.common.cancel}
        </Button>
      </div>
    </form>
  );
}
