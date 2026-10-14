import { useState, type FormEvent } from 'react';
import { errorMessage } from '../../api/errors';
import { useCreateGuild } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { NAME_MAX, TAG_MAX, nameProblem, tagProblem } from '../../game/guilds';
import { useI18n } from '../../i18n';
import styles from './Guild.module.css';

/** Found a guild: a name and a tag. The rules are checked here as well as on the server. */
export function CreateGuildCard() {
  const { t } = useI18n();
  const labels = t.guild.create;
  const create = useCreateGuild();
  const [name, setName] = useState('');
  const [tag, setTag] = useState('');
  // Say what is wrong only once there is something typed: an empty form is not an error yet.
  const nameError = name === '' ? undefined : nameProblem(name);
  const tagError = tag === '' ? undefined : tagProblem(tag);
  const valid = nameProblem(name) === undefined && tagProblem(tag) === undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (valid) {
      create.mutate({ name: name.trim(), tag: tag.trim() });
    }
  }

  return (
    <Card title={labels.title}>
      <form className={`${styles.body} ${styles.form}`} onSubmit={submit}>
        <TextField
          label={labels.name}
          value={name}
          maxLength={NAME_MAX + 10}
          autoComplete="off"
          hint={labels.nameHint}
          error={nameError === undefined ? undefined : labels.problems[nameError]}
          onChange={(event) => {
            setName(event.target.value);
            create.reset();
          }}
        />
        <TextField
          label={labels.tag}
          value={tag}
          maxLength={TAG_MAX + 5}
          autoComplete="off"
          autoCapitalize="off"
          hint={labels.tagHint}
          error={tagError === undefined ? undefined : labels.problems[tagError]}
          onChange={(event) => {
            setTag(event.target.value);
            create.reset();
          }}
        />
        {create.isError && <ErrorMessage>{errorMessage(t, create.error)}</ErrorMessage>}
        <div>
          <Button type="submit" disabled={!valid || create.isPending}>
            {labels.submit}
          </Button>
        </div>
      </form>
    </Card>
  );
}
