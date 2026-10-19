import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ApiError, errorMessage } from '../../api/errors';
import {
  pushQueryKey,
  usePushSettings,
  useSendPushTest,
  useSetPushKind,
  type PushSettings,
} from '../../api/queries/push';
import { BackLink } from '../../components/BackLink';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { useI18n } from '../../i18n';
import { devicePushOn, pushSupport, PushBlockedError, turnOffPush, turnOnPush } from '../../push/pushClient';
import styles from './NotificationsPage.module.css';

/**
 * /notifications (1.5, 05-frontend §7b): push notifications for this device, and the switches of
 * the kinds (one set per player, for all his devices).
 */
export function NotificationsPage() {
  const { t } = useI18n();
  const settings = usePushSettings();
  return (
    <div className={styles.page}>
      <BackLink to="/">{t.push.back}</BackLink>
      <Card title={t.push.title}>
        <div className={styles.body}>
          <p className={styles.text}>{t.push.intro}</p>
          {settings.isError && <ErrorMessage>{errorMessage(t, settings.error)}</ErrorMessage>}
          {settings.data !== undefined &&
            (settings.data.available && settings.data.publicKey !== null ? (
              <Device publicKey={settings.data.publicKey} devices={settings.data.devices} />
            ) : (
              <p className={styles.muted}>{t.push.unavailable}</p>
            ))}
        </div>
      </Card>
      {settings.data?.available === true && <Kinds kinds={settings.data.kinds} />}
    </div>
  );
}

type DeviceState = 'loading' | 'on' | 'off';

function Device({ publicKey, devices }: { publicKey: string; devices: number }) {
  const { t, language } = useI18n();
  const queryClient = useQueryClient();
  const support = pushSupport();
  const [state, setState] = useState<DeviceState>('loading');
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const test = useSendPushTest();

  useEffect(() => {
    let current = true;
    void devicePushOn().then((on) => {
      if (current) {
        setState(on ? 'on' : 'off');
      }
    });
    return () => {
      current = false;
    };
  }, []);

  if (support !== 'supported') {
    return <p className={styles.muted}>{support === 'ios-install' ? t.push.iosInstall : t.push.unsupported}</p>;
  }
  const blocked = Notification.permission === 'denied';

  async function change(on: boolean) {
    setBusy(true);
    setProblem(null);
    test.reset();
    try {
      if (on) {
        await turnOnPush(publicKey, language);
      } else {
        await turnOffPush();
      }
      setState(on ? 'on' : 'off');
    } catch (error) {
      setProblem(
        error instanceof PushBlockedError
          ? t.push.blocked
          : error instanceof ApiError
            ? errorMessage(t, error)
            : t.push.failed,
      );
    } finally {
      setBusy(false);
      void queryClient.invalidateQueries({ queryKey: pushQueryKey });
    }
  }

  return (
    <>
      <p className={styles.status}>
        {state === 'on' ? t.push.deviceOn : state === 'off' ? t.push.deviceOff : t.common.loading}
      </p>
      {devices > 0 && <p className={styles.muted}>{t.push.devices(devices)}</p>}
      {blocked && state !== 'on' && <ErrorMessage>{t.push.blocked}</ErrorMessage>}
      {problem !== null && <ErrorMessage>{problem}</ErrorMessage>}
      {test.isError && <ErrorMessage>{errorMessage(t, test.error)}</ErrorMessage>}
      {test.isSuccess && <p className={styles.saved}>{t.push.testSent}</p>}
      {state !== 'loading' && (
        <div className={styles.actions}>
          {state === 'off' ? (
            <Button type="button" disabled={busy || blocked} onClick={() => void change(true)}>
              {busy ? t.push.working : t.push.turnOn}
            </Button>
          ) : (
            <>
              <Button type="button" variant="secondary" disabled={busy} onClick={() => void change(false)}>
                {busy ? t.push.working : t.push.turnOff}
              </Button>
              <button
                type="button"
                className={styles.quiet}
                disabled={test.isPending || busy}
                onClick={() => test.mutate()}
              >
                {t.push.test}
              </button>
            </>
          )}
        </div>
      )}
    </>
  );
}

function Kinds({ kinds }: { kinds: PushSettings['kinds'] }) {
  const { t } = useI18n();
  const setKind = useSetPushKind();
  return (
    <Card title={t.push.kindsTitle}>
      <ul className={styles.kinds}>
        {kinds.map(({ kind, enabled }) => {
          const pending = setKind.isPending && setKind.variables.kind === kind;
          const checked = pending ? setKind.variables.enabled : enabled;
          return (
            <li key={kind}>
              <label className={styles.kind}>
                <span className={styles.kindText}>
                  <span className={styles.kindName}>{t.push.kinds[kind]}</span>
                  <span className={styles.kindHint}>{t.push.kindHints[kind]}</span>
                </span>
                <input
                  type="checkbox"
                  role="switch"
                  className={styles.switch}
                  checked={checked}
                  disabled={setKind.isPending}
                  onChange={(event) => setKind.mutate({ kind, enabled: event.target.checked })}
                />
              </label>
            </li>
          );
        })}
      </ul>
      {setKind.isError && (
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, setKind.error)}</ErrorMessage>
        </div>
      )}
    </Card>
  );
}
