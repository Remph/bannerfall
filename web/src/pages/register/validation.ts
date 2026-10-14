/** Same rules as the server (RegisterRequest.java), so most mistakes are caught before sending. */
export interface RegisterValues {
  username: string;
  password: string;
  inviteCode: string;
}

export type RegisterProblem = 'required' | 'usernameFormat' | 'passwordLength';

/** `Partial<Record<K, V>>`: an object that may have any of the keys K, each with a value V. */
export type RegisterProblems = Partial<Record<keyof RegisterValues, RegisterProblem>>;

const USERNAME_PATTERN = /^[A-Za-z0-9_-]{3,24}$/;

export function validateRegistration(values: RegisterValues): RegisterProblems {
  const problems: RegisterProblems = {};
  if (values.username === '') {
    problems.username = 'required';
  } else if (!USERNAME_PATTERN.test(values.username)) {
    problems.username = 'usernameFormat';
  }
  if (values.password === '') {
    problems.password = 'required';
  } else if (values.password.length < 8 || values.password.length > 128) {
    problems.password = 'passwordLength';
  }
  if (values.inviteCode.trim() === '') {
    problems.inviteCode = 'required';
  }
  return problems;
}
