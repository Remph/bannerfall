import { useState, type SubmitEvent } from 'react';
import { Link } from 'react-router';
import { ApiError, errorMessage, isPermanentBan } from '../../api/errors';
import { formatDateTime } from '../../game/format';
import { useBanNotice, useLogin } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useI18n } from '../../i18n';
import styles from '../auth.module.css';

export function LoginPage() {
  const { t, language } = useI18n();
  const login = useLogin();
  const banNotice = useBanNotice();
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

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1 className={styles.title}>{t.auth.loginTitle}</h1>
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
      <p className={styles.switch}>
        {t.auth.noAccount} <Link to="/register">{t.auth.toRegister}</Link>
      </p>
    </form>
  );
}
