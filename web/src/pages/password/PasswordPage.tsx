import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import {
  useAuthOptions,
  useChangePassword,
  useCurrentAccount,
  useExternalLogin,
  useLinkAccount,
} from '../../api/queries/auth';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import { externalProblemText } from '../login/externalLogin';
import styles from './PasswordPage.module.css';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;

/**
 * /password – the player changes his own password (08-host-console §11.3, M14d). With the account
 * server (1.5): a linked account changes it there instead; on a world that allows linking, an
 * unlinked one can link itself here.
 */
export function PasswordPage() {
  const { t, language } = useI18n();
  const labels = t.password;
  const me = useCurrentAccount();
  const options = useAuthOptions();
  const external = useExternalLogin();
  const link = useLinkAccount();
  const change = useChangePassword(language);
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [repeat, setRepeat] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [linkPassword, setLinkPassword] = useState('');
  const [linkSubmitted, setLinkSubmitted] = useState(false);

  const lengthError =
    submitted && (next.length < PASSWORD_MIN || next.length > PASSWORD_MAX)
      ? t.auth.validation.passwordLength
      : undefined;
  const mismatch = submitted && repeat !== next ? labels.mismatch : undefined;
  const currentMissing = submitted && current === '' ? t.auth.validation.required : undefined;

  const server = options.data?.accountServerName ?? null;
  const canLink = !me.linked && options.data?.mode === 'LINKING' && server !== null;
  const externalProblem = externalProblemText(t, language, external.data, server ?? '');

  if (me.linked) {
    return (
      <div className={styles.page}>
        <BackLink to="/">{labels.back}</BackLink>
        <Card title={labels.title}>
          {externalProblem !== null && (
            <div className={styles.form}>
              <ErrorMessage>{externalProblem}</ErrorMessage>
            </div>
          )}
          <p className={styles.text}>{server === null ? t.errors.ACCOUNT_LINKED : t.accountServer.linked(server)}</p>
        </Card>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <BackLink to="/">{labels.back}</BackLink>
      {canLink && (
        <Card title={t.accountServer.linkTitle(server)}>
          <form
            className={styles.form}
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              setLinkSubmitted(true);
              if (linkPassword !== '') {
                link.mutate({ currentPassword: linkPassword });
              }
            }}
          >
            {externalProblem !== null && <ErrorMessage>{externalProblem}</ErrorMessage>}
            <p className={styles.textInForm}>{t.accountServer.linkText(server)}</p>
            <TextField
              label={labels.current}
              type="password"
              autoComplete="current-password"
              value={linkPassword}
              error={linkSubmitted && linkPassword === '' ? t.auth.validation.required : undefined}
              onChange={(event) => setLinkPassword(event.target.value)}
            />
            {link.isError && <ErrorMessage>{errorMessage(t, link.error)}</ErrorMessage>}
            <Button type="submit" variant="secondary" disabled={link.isPending || link.isSuccess}>
              {link.isPending || link.isSuccess ? t.accountServer.linking : t.accountServer.linkButton(server)}
            </Button>
          </form>
        </Card>
      )}
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
