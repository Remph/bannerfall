import { useState } from 'react';
import { Link } from 'react-router';
import { errorMessage, isPermanentBan } from '../../api/errors';
import {
  useAdminPlayers,
  useBanPlayer,
  useChangeRole,
  useResetPassword,
  useUnbanPlayer,
  type AdminPlayer,
} from '../../api/queries/admin';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import { formatDateTime, formatNumber } from '../../game/format';
import { useI18n, useT } from '../../i18n';
import { playerPath } from '../ranking/rankingPaths';
import styles from './AdminPage.module.css';

type Action =
  { kind: 'role'; player: AdminPlayer } | { kind: 'reset'; player: AdminPlayer } | { kind: 'ban'; player: AdminPlayer };

/** GET /api/admin/players with the role change and the password reset (each asks first in a sheet). */
export function PlayersCard() {
  const { t, language } = useI18n();
  const labels = t.admin.players;
  const players = useAdminPlayers();
  const [action, setAction] = useState<Action | null>(null);

  return (
    <Card title={labels.title}>
      <div className={styles.stack}>
        <p className={styles.hint}>{labels.hint}</p>
        {players.isError && <ErrorMessage>{errorMessage(t, players.error)}</ErrorMessage>}
        {players.isPending && <p>{t.common.loading}</p>}
        {players.data?.length === 0 && <p className={styles.hint}>{labels.empty}</p>}
        <ul className={styles.lords}>
          {players.data?.map((player) => (
            <li key={player.playerId} className={styles.lord}>
              <div className={styles.lordHead}>
                <strong>
                  {player.name}
                  {player.role === 'ADMIN' && <span className={styles.tag}>{labels.adminTag}</span>}
                  {player.deleted && <span className={styles.tag}>{labels.deletedTag}</span>}
                </strong>
                <Link to={playerPath(player.playerId)}>{labels.profile}</Link>
              </div>
              <p className={styles.meta}>
                {labels.points(formatNumber(player.points, language))} · {labels.villages(player.villageCount)} ·{' '}
                {player.lastLoginAt == null
                  ? labels.neverLoggedIn
                  : labels.lastLogin(formatDateTime(Date.parse(player.lastLoginAt), language))}
              </p>
              {player.bannedUntil != null && (
                <p className={styles.bad}>
                  {isPermanentBan(player.bannedUntil)
                    ? labels.bannedForGood(player.banReason ?? '')
                    : labels.bannedUntil(
                        formatDateTime(Date.parse(player.bannedUntil), language),
                        player.banReason ?? '',
                      )}
                </p>
              )}
              {!player.deleted && (
                <div className={styles.actions}>
                  <Button
                    type="button"
                    variant="secondary"
                    size="small"
                    onClick={() => setAction({ kind: 'role', player })}
                  >
                    {player.role === 'ADMIN' ? labels.makePlayer : labels.makeAdmin}
                  </Button>
                  <Button
                    type="button"
                    variant="secondary"
                    size="small"
                    onClick={() => setAction({ kind: 'reset', player })}
                  >
                    {labels.resetPassword}
                  </Button>
                  {player.bannedUntil != null ? (
                    <UnbanButton player={player} />
                  ) : (
                    player.role !== 'ADMIN' && (
                      <Button
                        type="button"
                        variant="secondary"
                        size="small"
                        onClick={() => setAction({ kind: 'ban', player })}
                      >
                        {labels.ban}
                      </Button>
                    )
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
      {action?.kind === 'role' && <RoleSheet player={action.player} onClose={() => setAction(null)} />}
      {action?.kind === 'ban' && <BanSheet player={action.player} onClose={() => setAction(null)} />}
      {action?.kind === 'reset' && <ResetSheet player={action.player} onClose={() => setAction(null)} />}
    </Card>
  );
}

function UnbanButton({ player }: { player: AdminPlayer }) {
  const t = useT();
  const unban = useUnbanPlayer();
  return (
    <>
      <Button
        type="button"
        variant="secondary"
        size="small"
        disabled={unban.isPending}
        onClick={() => unban.mutate(player.playerId)}
      >
        {t.admin.players.unban}
      </Button>
      {unban.isError && <ErrorMessage>{errorMessage(t, unban.error)}</ErrorMessage>}
    </>
  );
}

const BAN_DAYS = ['1', '7', '30', 'forever'] as const;

/** Ban for a day, a week, a month or for good, with the reason the player sees at login. */
function BanSheet({ player, onClose }: { player: AdminPlayer; onClose: () => void }) {
  const t = useT();
  const labels = t.admin.players;
  const ban = useBanPlayer();
  const [length, setLength] = useState<(typeof BAN_DAYS)[number]>('1');
  const [reason, setReason] = useState('');
  const missing = reason.trim() === '';
  return (
    <Sheet open onClose={onClose} title={labels.banTitle(player.name)}>
      <form
        className={styles.confirm}
        onSubmit={(event) => {
          event.preventDefault();
          if (missing) return;
          const until = length === 'forever' ? null : new Date(Date.now() + Number(length) * 86_400_000).toISOString();
          ban.mutate({ id: player.playerId, until, reason: reason.trim() }, { onSuccess: onClose });
        }}
      >
        <p className={styles.hint}>{labels.banHint}</p>
        <div role="radiogroup" aria-label={labels.banLength} className={styles.actions}>
          {BAN_DAYS.map((value) => (
            <Button
              key={value}
              type="button"
              size="small"
              variant={value === length ? 'primary' : 'secondary'}
              aria-pressed={value === length}
              onClick={() => setLength(value)}
            >
              {labels.banLengths[value]}
            </Button>
          ))}
        </div>
        <TextField
          label={labels.banReason}
          value={reason}
          maxLength={200}
          onChange={(event) => setReason(event.target.value)}
        />
        {ban.isError && <ErrorMessage>{errorMessage(t, ban.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button type="submit" variant="danger" disabled={missing || ban.isPending}>
            {labels.banConfirm}
          </Button>
        </div>
      </form>
    </Sheet>
  );
}

function RoleSheet({ player, onClose }: { player: AdminPlayer; onClose: () => void }) {
  const t = useT();
  const labels = t.admin.players;
  const change = useChangeRole();
  const toAdmin = player.role !== 'ADMIN';
  return (
    <Sheet open onClose={onClose} title={labels.roleTitle}>
      <div className={styles.confirm}>
        <p>{labels.roleText(player.name, toAdmin)}</p>
        {change.isError && <ErrorMessage>{errorMessage(t, change.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={change.isPending}
            onClick={() =>
              change.mutate({ id: player.playerId, role: toAdmin ? 'ADMIN' : 'PLAYER' }, { onSuccess: onClose })
            }
          >
            {labels.roleConfirm}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}

/** The new password: typed by the host, or generated and shown once (then the sheet only offers Close). */
function ResetSheet({ player, onClose }: { player: AdminPlayer; onClose: () => void }) {
  const t = useT();
  const labels = t.admin.reset;
  const reset = useResetPassword();
  const [password, setPassword] = useState('');
  const tooShort = password !== '' && (password.length < 8 || password.length > 128);

  return (
    <Sheet open onClose={onClose} title={labels.title(player.name)}>
      {reset.isSuccess ? (
        <div className={styles.confirm}>
          {reset.data.generatedPassword == null ? (
            <p className={styles.saved}>{labels.done}</p>
          ) : (
            <>
              <p>{labels.generated}</p>
              <p className={styles.password}>{reset.data.generatedPassword}</p>
            </>
          )}
          <div className={styles.actions}>
            <Button type="button" onClick={onClose}>
              {t.common.close}
            </Button>
          </div>
        </div>
      ) : (
        <form
          className={styles.confirm}
          onSubmit={(event) => {
            event.preventDefault();
            if (!tooShort) {
              reset.mutate({ id: player.playerId, password: password === '' ? null : password });
            }
          }}
        >
          <p className={styles.hint}>{labels.hint}</p>
          <TextField
            label={labels.label}
            type="text"
            autoComplete="off"
            value={password}
            maxLength={128}
            error={tooShort ? t.auth.validation.passwordLength : undefined}
            onChange={(event) => setPassword(event.target.value)}
          />
          {reset.isError && <ErrorMessage>{errorMessage(t, reset.error)}</ErrorMessage>}
          <div className={styles.actions}>
            <Button type="button" variant="secondary" onClick={onClose}>
              {t.common.cancel}
            </Button>
            <Button type="submit" variant="danger" disabled={tooShort || reset.isPending}>
              {labels.confirm}
            </Button>
          </div>
        </form>
      )}
    </Sheet>
  );
}
