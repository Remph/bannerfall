import { useState, type SubmitEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError, errorMessage } from '../../api/errors';
import { ACCOUNT_SERVER_LOGIN, useAuthOptions, useExternalLogin, useJoin } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useI18n, useT } from '../../i18n';
import styles from '../auth.module.css';
import { externalProblemText } from '../login/externalLogin';

/**
 * /join (1.5): somebody logged in at the account server who has no player on this world yet. The
 * server keeps that login in the session; this page asks for the world's invite code, then the
 * player starts under the account server's name.
 */
export function JoinPage() {
  const { t, language } = useI18n();
  const options = useAuthOptions();
  const external = useExternalLogin();
  const join = useJoin();
  // like /register?invite=abc, /join?invite=abc fills in the code
  const [searchParams] = useSearchParams();
  const [inviteCode, setInviteCode] = useState(searchParams.get('invite') ?? '');
  const [submitted, setSubmitted] = useState(false);

  const server = options.data?.accountServerName ?? '';
  const name = external.data?.name ?? null;
  const codeMissing = inviteCode.trim() === '';

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (!codeMissing) {
      // On success the player is logged in and <RequireGuest> opens the game.
      join.mutate(inviteCode.trim());
    }
  }

  if (options.isPending || external.isPending) {
    return <h1 className={styles.title}>{t.accountServer.joinTitle}</h1>;
  }

  if (name === null) {
    const problem = externalProblemText(t, language, external.data, server);
    return (
      <div className={styles.form}>
        <h1 className={styles.title}>{t.accountServer.joinTitle}</h1>
        {problem !== null && <ErrorMessage>{problem}</ErrorMessage>}
        <p className={styles.hint}>{t.accountServer.joinMissing(server)}</p>
        <p className={styles.switch}>
          <Link to="/login">{t.auth.toLogin}</Link>
        </p>
      </div>
    );
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1 className={styles.title}>{t.accountServer.joinTitle}</h1>
      <p className={styles.hint}>{t.accountServer.joinText(name, server)}</p>
      {join.isError && (
        <JoinError error={join.error} name={name} server={server} linking={options.data?.mode === 'LINKING'} />
      )}
      <TextField
        label={t.auth.inviteCode}
        value={inviteCode}
        onChange={(event) => setInviteCode(event.target.value)}
        autoComplete="off"
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        hint={t.auth.inviteCodeHint}
        error={submitted && codeMissing ? t.auth.validation.required : undefined}
      />
      <Button type="submit" disabled={join.isPending}>
        {join.isPending ? t.accountServer.joining : t.accountServer.joinButton}
      </Button>
    </form>
  );
}

/** Why joining failed, with the way on where there is one. */
function JoinError({ error, name, server, linking }: { error: Error; name: string; server: string; linking: boolean }) {
  const t = useT();
  const code = error instanceof ApiError ? error.code : null;
  if (code === 'EXTERNAL_LOGIN_MISSING') {
    // the login at the account server waited too long (the session ended): once more from there
    return (
      <ErrorMessage>
        {errorMessage(t, error)} <a href={ACCOUNT_SERVER_LOGIN}>{t.accountServer.loginButton(server)}</a>
      </ErrorMessage>
    );
  }
  if (code === 'USERNAME_TAKEN' && linking) {
    // most likely the player's own local account here: linking it is the way in
    return (
      <ErrorMessage>
        {t.accountServer.joinNameTaken(name, server)} <Link to="/login">{t.auth.toLogin}</Link>
      </ErrorMessage>
    );
  }
  return <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>;
}
