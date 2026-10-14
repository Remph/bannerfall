import { describe, expect, it } from 'vitest';
import { validateRegistration } from './validation';

const valid = { username: 'Ben_99', password: 'correct horse', inviteCode: 'secret' };

describe('validateRegistration', () => {
  it('accepts valid input', () => {
    expect(validateRegistration(valid)).toEqual({});
  });

  it('reports empty fields', () => {
    expect(validateRegistration({ username: '', password: '', inviteCode: '  ' })).toEqual({
      username: 'required',
      password: 'required',
      inviteCode: 'required',
    });
  });

  it('checks the username format', () => {
    expect(validateRegistration({ ...valid, username: 'ab' }).username).toBe('usernameFormat');
    expect(validateRegistration({ ...valid, username: 'a'.repeat(25) }).username).toBe('usernameFormat');
    expect(validateRegistration({ ...valid, username: 'Ben Name' }).username).toBe('usernameFormat');
    expect(validateRegistration({ ...valid, username: 'Jörg' }).username).toBe('usernameFormat');
    expect(validateRegistration({ ...valid, username: 'a-b_c' }).username).toBeUndefined();
  });

  it('checks the password length', () => {
    expect(validateRegistration({ ...valid, password: '1234567' }).password).toBe('passwordLength');
    expect(validateRegistration({ ...valid, password: '12345678' }).password).toBeUndefined();
    expect(validateRegistration({ ...valid, password: 'x'.repeat(129) }).password).toBe('passwordLength');
  });
});
