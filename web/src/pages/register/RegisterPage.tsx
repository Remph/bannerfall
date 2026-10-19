import { useState, type ChangeEvent, type SubmitEvent } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useAuthOptions, useRegister } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { useT } from '../../i18n';
import styles from '../auth.module.css';
import { validateRegistration, type RegisterProblem, type RegisterValues } from './validation';

export function RegisterPage() {
  const t = useT();
  const register = useRegister();
  const options = useAuthOptions();
  // An invite link like /register?invite=abc fills in the code.
  const [searchParams] = useSearchParams();
  const [values, setValues] = useState<RegisterValues>({
    username: '',
    password: '',
    inviteCode: searchParams.get('invite') ?? '',
  });
  const [submitted, setSubmitted] = useState(false);

  const problems = validateRegistration(values);
  const shownProblem = (field: keyof RegisterValues): string | undefined => {
    const problem: RegisterProblem | undefined = problems[field];
    return submitted && problem !== undefined ? t.auth.validation[problem] : undefined;
  };

  // One change handler for all fields: `field` says which value to replace.
  const update = (field: keyof RegisterValues) => (event: ChangeEvent<HTMLInputElement>) =>
    setValues((previous) => ({ ...previous, [field]: event.target.value }));

  function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitted(true);
    if (Object.keys(problems).length > 0) {
      return;
    }
    register.mutate({ ...values, inviteCode: values.inviteCode.trim() });
  }

  // a world without its own accounts (1.5): new players come through the account server
  if (options.data?.mode === 'REQUIRED' && options.data.accountServerName !== null) {
    return <Navigate to="/login" replace />;
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <h1 className={styles.title}>{t.auth.registerTitle}</h1>
      {register.isError && <ErrorMessage>{errorMessage(t, register.error)}</ErrorMessage>}
      <TextField
        label={t.auth.username}
        value={values.username}
        onChange={update('username')}
        autoComplete="username"
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        maxLength={24}
        hint={t.auth.usernameHint}
        error={shownProblem('username')}
      />
      <TextField
        label={t.auth.password}
        type="password"
        value={values.password}
        onChange={update('password')}
        autoComplete="new-password"
        maxLength={128}
        hint={t.auth.passwordHint}
        error={shownProblem('password')}
      />
      <TextField
        label={t.auth.inviteCode}
        value={values.inviteCode}
        onChange={update('inviteCode')}
        autoComplete="off"
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        hint={t.auth.inviteCodeHint}
        error={shownProblem('inviteCode')}
      />
      <Button type="submit" disabled={register.isPending}>
        {register.isPending ? t.auth.registering : t.auth.registerButton}
      </Button>
      <p className={styles.switch}>
        {t.auth.haveAccount} <Link to="/login">{t.auth.toLogin}</Link>
      </p>
    </form>
  );
}
