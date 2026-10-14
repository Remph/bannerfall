import { errorMessage } from '../api/errors';
import { useDevAddResources, useDevAddUnits, useDevFinishMovements, useDevFinishOrders } from '../api/queries/dev';
import { formatNumber } from '../game/format';
import { useI18n } from '../i18n';
import { Button } from './Button';
import { Card } from './Card';
import styles from './DevCheats.module.css';
import { ErrorMessage } from './ErrorMessage';

const RESOURCE_GIFT = 10_000;
const UNIT_GIFT = 100;

interface DevCheatsProps {
  villageId: number;
  /** Also offer "let all movements arrive now" (rally point). */
  movements?: boolean;
}

/**
 * Shortcuts for trying things out (Town Hall, recruit and rally point screens). Only rendered in
 * `pnpm dev` (import.meta.env.DEV); the endpoints only exist with the server's `dev` profile.
 */
export function DevCheats({ villageId, movements = false }: DevCheatsProps) {
  const { t, language } = useI18n();
  const addResources = useDevAddResources(villageId);
  const addUnits = useDevAddUnits(villageId);
  const finish = useDevFinishOrders(villageId);
  const finishMovements = useDevFinishMovements(villageId);
  const error = addResources.error ?? addUnits.error ?? finish.error ?? finishMovements.error;
  // Each button first clears all results, so only the error of the last action is shown.
  function clearResults() {
    addResources.reset();
    addUnits.reset();
    finish.reset();
    finishMovements.reset();
  }
  return (
    <Card title={t.dev.title}>
      <div className={styles.body}>
        <p className={styles.hint}>{t.dev.hint}</p>
        <div className={styles.actions}>
          <Button
            type="button"
            variant="secondary"
            disabled={addResources.isPending}
            onClick={() => {
              clearResults();
              addResources.mutate(RESOURCE_GIFT);
            }}
          >
            {t.dev.addResources(formatNumber(RESOURCE_GIFT, language))}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={addUnits.isPending}
            onClick={() => {
              clearResults();
              addUnits.mutate(UNIT_GIFT);
            }}
          >
            {t.dev.addUnits(formatNumber(UNIT_GIFT, language))}
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={finish.isPending}
            onClick={() => {
              clearResults();
              finish.mutate();
            }}
          >
            {t.dev.finishOrders}
          </Button>
          {movements && (
            <Button
              type="button"
              variant="secondary"
              disabled={finishMovements.isPending}
              onClick={() => {
                clearResults();
                finishMovements.mutate();
              }}
            >
              {t.dev.finishMovements}
            </Button>
          )}
        </div>
        {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
      </div>
    </Card>
  );
}
