import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useDescribeGuild, type Guild } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { DESCRIPTION_MAX, charCount } from '../../game/guilds';
import { useI18n } from '../../i18n';
import styles from './Guild.module.css';

/** What the guild says about itself; its leader can change it. */
export function DescriptionCard({ guild, isLeader }: { guild: Guild; isLeader: boolean }) {
  const { t } = useI18n();
  const labels = t.guild.leader;
  const describe = useDescribeGuild();
  const [draft, setDraft] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (draft !== null && charCount(draft.trim()) <= DESCRIPTION_MAX) {
      describe.mutate(draft, { onSuccess: () => setDraft(null) });
    }
  }

  return (
    <Card title={t.guild.description}>
      {draft === null ? (
        <div className={styles.body}>
          {guild.description === '' ? (
            <p className={styles.hint}>{t.guild.noDescription}</p>
          ) : (
            <p className={styles.text}>{guild.description}</p>
          )}
          {isLeader && (
            <div>
              <Button type="button" size="small" variant="secondary" onClick={() => setDraft(guild.description)}>
                {labels.editDescription}
              </Button>
            </div>
          )}
        </div>
      ) : (
        <form className={`${styles.body} ${styles.form}`} onSubmit={submit}>
          <label className={styles.label}>
            {labels.descriptionLabel}
            <textarea
              className={styles.textarea}
              value={draft}
              onChange={(event) => {
                setDraft(event.target.value);
                describe.reset();
              }}
            />
            <span className={styles.hint}>{labels.descriptionHint(DESCRIPTION_MAX - charCount(draft.trim()))}</span>
          </label>
          {describe.isError && <ErrorMessage>{errorMessage(t, describe.error)}</ErrorMessage>}
          <div className={styles.buttons}>
            <Button type="submit" disabled={describe.isPending || charCount(draft.trim()) > DESCRIPTION_MAX}>
              {t.common.save}
            </Button>
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setDraft(null);
                describe.reset();
              }}
            >
              {t.common.cancel}
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}
