import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount, useRespawn } from '../../api/queries/auth';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { useT } from '../../i18n';
import { rememberVillage } from '../../layout/currentVillage';
import styles from './RespawnPage.module.css';

/**
 * The player lost his last village (`needsRespawn`, 02-game-rules §10): what happened, the way to
 * the reports, and a new start with a fresh village. "/" shows it while there is no village. When
 * a guild mate has a village (`respawnNearGuild`, 1.6) the player picks: next to the guild (the
 * main button) or somewhere new.
 */
export function RespawnPage() {
  const t = useT();
  const navigate = useNavigate();
  const respawn = useRespawn();
  const { respawnNearGuild } = useCurrentAccount();
  // which button was pressed, so only that one says "Founding your village…"
  const [chosen, setChosen] = useState<boolean | null>(null);

  const start = (nearGuild: boolean) => {
    setChosen(nearGuild);
    respawn.mutate(nearGuild, {
      onSuccess: (village) => {
        rememberVillage(village.id);
        void navigate(`/v/${village.id}`, { replace: true });
      },
    });
  };
  const label = (nearGuild: boolean, idle: string) =>
    respawn.isPending && chosen === nearGuild ? t.respawn.starting : idle;

  return (
    <div className={styles.page}>
      <Card title={t.respawn.title}>
        <div className={styles.body}>
          <p>{t.respawn.text}</p>
          {respawnNearGuild && <p>{t.respawn.guildText}</p>}
          <p className={styles.muted}>
            {t.respawn.reportsHint} <Link to="/reports">{t.respawn.toReports}</Link>
          </p>
          {respawn.isError && <ErrorMessage>{errorMessage(t, respawn.error)}</ErrorMessage>}
          {respawnNearGuild ? (
            <div className={styles.choices}>
              <Button type="button" disabled={respawn.isPending} onClick={() => start(true)}>
                {label(true, t.respawn.nearGuild)}
              </Button>
              <Button type="button" variant="secondary" disabled={respawn.isPending} onClick={() => start(false)}>
                {label(false, t.respawn.elsewhere)}
              </Button>
            </div>
          ) : (
            <Button type="button" disabled={respawn.isPending} onClick={() => start(false)}>
              {label(false, t.respawn.start)}
            </Button>
          )}
        </div>
      </Card>
    </div>
  );
}
