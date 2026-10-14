import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useChangePassword } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import styles from './PasswordPage.module.css';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;

/** /password – the player changes his own password (08-host-console §11.3, M14d). */
export function PasswordPage() {
  const { t } = useI18n();
  const labels = t.password;
  const change = useChangePassword();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const lengthError =
    submitted && (next.length < PASSWORD_MIN || next.length > PASSWORD_MAX)
      ? t.auth.validation.passwordLength
      : undefined;
  const mismatch = submitted && repeat !== next ? labels.mismatch : undefined;
  const currentMissing = submitted && current === '' ? t.auth.validation.required : undefined;

  return (
    <div className={styles.page}>
      <Link to="/" className={styles.back}>
        <Icon name="chevronLeft" /> {labels.back}
      </Link>
      <Card title={labels.title}>
        {change.isSuccess ? (
          <p className={styles.saved}>{labels.saved}</p>
        ) : (
          <form
            className={styles.form}
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              setSubmitted(true);
              const valid =
                current !== '' && next.length >= PASSWORD_MIN && next.length <= PASSWORD_MAX && repeat === next;
              if (valid) {
                change.mutate({ currentPassword: current, newPassword: next });
              }
            }}
          >
            <TextField
              label={labels.current}
              type="password"
              autoComplete="current-password"
              value={current}
              error={currentMissing}
              onChange={(event) => setCurrent(event.target.value)}
            />
            <TextField
              label={labels.new}
              type="password"
              autoComplete="new-password"
              value={next}
              maxLength={PASSWORD_MAX}
              hint={t.auth.passwordHint}
              error={lengthError}
              onChange={(event) => setNext(event.target.value)}
            />
            <TextField
              label={labels.repeat}
              type="password"
              autoComplete="new-password"
              value={repeat}
              maxLength={PASSWORD_MAX}
              error={mismatch}
              onChange={(event) => setRepeat(event.target.value)}
            />
            {change.isError && <ErrorMessage>{errorMessage(t, change.error)}</ErrorMessage>}
            <Button type="submit" disabled={change.isPending}>
              {change.isPending ? labels.saving : labels.save}
            </Button>
          </form>
        )}
      </Card>
    </div>
  );
}
