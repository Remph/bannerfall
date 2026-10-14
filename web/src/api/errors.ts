import type { Dictionary } from '../i18n/en';

export interface FieldError {
  field: string;
  message: string;
}

/**
 * Every failed API call is thrown as an ApiError (see client.ts), so components and TanStack Query
 * only deal with one error type. `code` is the server's ErrorCode or a client code (NETWORK, UNKNOWN).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: readonly FieldError[];
  /** ACCOUNT_BANNED (M14e): when the ban ends (ISO instant) and why; null otherwise. */
  readonly bannedUntil: string | null;
  readonly banReason: string | null;
  /** "Recruit all" (M27): the unit whose order failed (all orders were refused); null otherwise. */
  readonly unit: string | null;
  /** Sending a report (M40): the name the send stopped at; null otherwise. */
  readonly playerName: string | null;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: readonly FieldError[] = [],
    ban: { until: string; reason: string } | null = null,
    unit: string | null = null,
    playerName: string | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.bannedUntil = ban?.until ?? null;
    this.banReason = ban === null || ban.reason === '' ? null : ban.reason;
    this.unit = unit;
    this.playerName = playerName;
  }
}

/** No response at all (server down, offline). Status 0 like the browser reports it. */
export function networkError(): ApiError {
  return new ApiError(0, 'NETWORK', 'Server not reachable');
}

/** Turns a parsed ProblemDetail body (or anything else the server or a proxy sent) into an ApiError. */
export function toApiError(status: number, body: unknown): ApiError {
  if (!isRecord(body)) {
    return new ApiError(status, 'UNKNOWN', `HTTP ${status}`);
  }
  const code = typeof body.code === 'string' ? body.code : 'UNKNOWN';
  const detail = typeof body.detail === 'string' ? body.detail : `HTTP ${status}`;
  const fieldErrors = Array.isArray(body.errors) ? body.errors.filter(isFieldError) : [];
  const ban =
    typeof body.bannedUntil === 'string'
      ? { until: body.bannedUntil, reason: typeof body.reason === 'string' ? body.reason : '' }
      : null;
  const unit = typeof body.unit === 'string' ? body.unit : null;
  const playerName = typeof body.name === 'string' ? body.name : null;
  return new ApiError(status, code, detail, fieldErrors, ban, unit, playerName);
}

/** A ban lasting until the year 9999 is "for good". */
export function isPermanentBan(until: string): boolean {
  return Date.parse(until) > Date.UTC(9000, 0, 1);
}

export async function responseToApiError(response: Response): Promise<ApiError> {
  let body: unknown = null;
  try {
    body = await response.clone().json();
  } catch {
    // not JSON, e.g. an HTML error page from a proxy
  }
  return toApiError(response.status, body);
}

/** The translated message for any error (unknown codes and non-API errors get a generic text). */
export function errorMessage(t: Dictionary, error: unknown): string {
  if (error instanceof ApiError && isErrorCode(t, error.code)) {
    return t.errors[error.code];
  }
  return t.errors.UNKNOWN;
}

type ErrorCode = keyof Dictionary['errors'];

function isErrorCode(t: Dictionary, code: string): code is ErrorCode {
  return Object.hasOwn(t.errors, code);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isFieldError(value: unknown): value is FieldError {
  return isRecord(value) && typeof value.field === 'string' && typeof value.message === 'string';
}
