import { useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';
import { ApiError, errorMessage, isPermanentBan } from '../../api/errors';
import { formatDateTime } from '../../game/format';
import { ACCOUNT_SERVER_LOGIN, useAuthOptions, useBanNotice, useExternalLogin, useLogin } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import styles from '../auth.module.css';
import { externalProblemText } from './externalLogin';

export function LoginPage() {
  const { t, language } = useI18n();
  const login = useLogin();
  const banNotice = useBanNotice();
  const options = useAuthOptions();
  const external = useExternalLogin();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  // Show "required" only after the first submit, not while the user is still typing.
  const [submitted, setSubmitted] = useState(false);

  const usernameMissing = username.trim() === '';
  const passwordMissing = password === '';

  function banText(until: string, reason: string | null): string {
    return isPermanentBan(until)
      ? t.auth.bannedForGood(reason)
      : t.auth.bannedUntil(formatDateTime(Date.parse(until), language), reason);
  }

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault(); // no page reload – we send the data ourselves
    setSubmitted(true);
    if (usernameMissing || passwordMissing) {
      return;
    }
    // On success the user is logged in and <RequireGuest> redirects to the game.
    login.mutate({ username: username.trim(), password });
  }

  // Until the options are known nothing but the title shows (no password form flashing up on a
  // world without passwords); if they can't be loaded, the world's own login is offered.
  const mode = options.data?.mode ?? (options.isError ? 'OFF' : null);
  const server = options.data?.accountServerName ?? null;
  const withAccountServer = mode !== null && mode !== 'OFF' && server !== null;
  const passwords = mode === 'OFF' || mode === 'LINKING' || (mode === 'REQUIRED' && server === null);
  const externalProblem = externalProblemText(t, language, external.data, server ?? '');

  if (!passwords) {
    return (
      <div className={styles.form}>
        <h1 className={styles.title}>{t.auth.loginTitle}</h1>
        {externalProblem !== null && <ErrorMessage>{externalProblem}</ErrorMessage>}
        {withAccountServer && (
          <>
            <p className={styles.hint}>{t.accountServer.requiredHint(server)}</p>
            <Button type="button" onClick={() => window.location.assign(ACCOUNT_SERVER_LOGIN)}>
              {t.accountServer.loginButton(server)}
            </Button>
          </>
        )}
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1 className={styles.title}>{t.auth.loginTitle}</h1>
      {externalProblem !== null && !login.isError && <ErrorMessage>{externalProblem}</ErrorMessage>}
      {withAccountServer && (
        <>
          <Button type="button" variant="secondary" onClick={() => window.location.assign(ACCOUNT_SERVER_LOGIN)}>
            {t.accountServer.loginButton(server)}
          </Button>
          <p className={styles.divider}>{t.accountServer.or}</p>
        </>
      )}
      {login.isError ? (
        <ErrorMessage>
          {login.error instanceof ApiError && login.error.bannedUntil !== null
            ? banText(login.error.bannedUntil, login.error.banReason)
            : errorMessage(t, login.error)}
        </ErrorMessage>
      ) : (
        // banned while playing: the session ended and this is where the player lands
        banNotice !== null && <ErrorMessage>{banText(banNotice.until, banNotice.reason)}</ErrorMessage>
      )}
      <TextField
        label={t.auth.username}
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        autoComplete="username"
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        error={submitted && usernameMissing ? t.auth.validation.required : undefined}
      />
      <TextField
        label={t.auth.password}
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        autoComplete="current-password"
        error={submitted && passwordMissing ? t.auth.validation.required : undefined}
      />
      <Button type="submit" disabled={login.isPending}>
        {login.isPending ? t.auth.loggingIn : t.auth.loginButton}
      </Button>
      {withAccountServer && <p className={styles.hint}>{t.accountServer.linkingHint(server)}</p>}
      <p className={styles.switch}>
        {t.auth.noAccount} <Link to="/register">{t.auth.toRegister}</Link>
      </p>
    </form>
  );
}
