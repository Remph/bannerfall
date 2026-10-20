import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useSendResources, type Market, type Transport } from '../../api/queries/market';
import type { Village } from '../../api/queries/villages';
import { useWorld } from '../../api/queries/world';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ErrorMessage } from '../../components/ErrorMessage';
import { TextField } from '../../components/TextField';
import { isOnMap, parseCoords } from '../../game/coords';
import { formatDuration, formatNumber, formatWhenExact } from '../../game/format';
import { maxSendable, merchantsNeeded, totalOf, travelSeconds } from '../../game/market';
import { projectResources, resourceTypes, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { targetFromSearch } from '../rally/rallyPaths';
import { useTargetVillage } from '../rally/useTargetVillage';
import { villageLabel } from '../rally/villageLabel';
import { AmountField } from './AmountField';
import styles from './MarketPage.module.css';
import { OwnVillagePicker } from './OwnVillagePicker';

const NOTHING: ResourceAmounts = { wood: 0, clay: 0, iron: 0 };

/**
 * Send resources (05-frontend §3): the target field (prefilled from `?x=&y=`, the map's button,
 * or picked from the player's own villages), how much of each resource, and what that takes –
 * merchants and travel time. Sending is final (the goods leave at once), so the summary above the
 * button says everything the server will do.
 */
export function SendResourcesCard({ village, market }: { village: Village; market: Market }) {
  const { t, language } = useI18n();
  const now = useGameNow();
  const [searchParams] = useSearchParams();
  const [target, setTarget] = useState(() => targetFromSearch(searchParams));
  const [targetError, setTargetError] = useState<string | undefined>();
  const [amounts, setAmounts] = useState<ResourceAmounts>(NOTHING);
  const [sent, setSent] = useState<Transport | null>(null);
  const send = useSendResources(village.id);
  const world = useWorld().data;

  const parsed = parseCoords(target);
  const field = parsed !== undefined && (world === undefined || isOnMap(parsed, world.mapSize)) ? parsed : undefined;
  const targetVillage = useTargetVillage(field, village);

  const stock = projectResources(village, now);
  const free = market.merchants.free;
  const total = totalOf(amounts);
  const needed = merchantsNeeded(total);
  const tooManyMerchants = needed > free;
  const tooMuch = resourceTypes.some((type) => amounts[type] > Math.floor(stock[type]));

  function change(type: keyof ResourceAmounts, value: number) {
    setAmounts((previous) => ({ ...previous, [type]: value }));
    setSent(null);
    send.reset();
  }

  function submit() {
    setSent(null);
    let error: string | undefined;
    if (parsed === undefined) {
      error = t.map.invalidCoords;
    } else if (field === undefined && world !== undefined) {
      error = t.map.offMap(world.mapSize - 1);
    } else if (targetVillage.kind === 'none') {
      error = t.errors.TARGET_NOT_FOUND;
    } else if (targetVillage.kind === 'found' && targetVillage.village.id === village.id) {
      error = t.errors.SAME_VILLAGE;
    }
    setTargetError(error);
    if (error !== undefined || parsed === undefined) {
      return;
    }
    send.mutate(
      { target: parsed, ...amounts },
      {
        onSuccess: (transport) => {
          setSent(transport);
          setAmounts(NOTHING);
        },
      },
    );
  }

  let hint: string;
  switch (targetVillage.kind) {
    case 'unknown':
      hint = t.rally.targetHint;
      break;
    case 'loading':
      hint = t.rally.targetLoading;
      break;
    case 'none':
      hint = t.rally.noVillageThere;
      break;
    case 'found':
      hint = targetVillage.text;
  }
  const travel =
    targetVillage.kind === 'found' && targetVillage.village.id !== village.id && world !== undefined
      ? travelSeconds(targetVillage.distance, world)
      : undefined;

  return (
    <Card title={t.market.sendTitle}>
      <div className={styles.body}>
        <div className={styles.target}>
          <TextField
            label={t.rally.target}
            value={target}
            onChange={(event) => {
              setTarget(event.target.value);
              setTargetError(undefined);
              setSent(null);
            }}
            inputMode="decimal"
            autoComplete="off"
            spellCheck={false}
            placeholder="48|52"
            hint={hint}
            error={targetError}
          />
          <OwnVillagePicker
            village={village}
            target={field}
            onPick={(coords) => {
              setTarget(`${coords.x}|${coords.y}`);
              setTargetError(undefined);
              setSent(null);
            }}
          />
        </div>
        <ul className={styles.amounts}>
          {resourceTypes.map((type) => (
            <AmountField
              key={type}
              resource={type}
              value={amounts[type]}
              onChange={(value) => change(type, value)}
              max={maxSendable(stock[type], free, total - amounts[type])}
            />
          ))}
        </ul>
        <dl className={styles.facts}>
          <div>
            <dt>{t.market.total}</dt>
            <dd>{formatNumber(total, language)}</dd>
          </div>
          <div className={tooManyMerchants ? styles.short : undefined}>
            <dt>{t.market.merchantsNeeded}</dt>
            <dd>{t.market.ofFree(formatNumber(needed, language), formatNumber(free, language))}</dd>
          </div>
          {travel !== undefined && (
            <>
              <div>
                <dt>{t.market.travelTime}</dt>
                <dd>{formatDuration(travel)}</dd>
              </div>
              <div>
                <dt>{t.market.arrival}</dt>
                <dd>{formatWhenExact(now + travel * 1000, now, language)}</dd>
              </div>
            </>
          )}
        </dl>
        {tooMuch && <ErrorMessage>{t.errors.NOT_ENOUGH_RESOURCES}</ErrorMessage>}
        {tooManyMerchants && !tooMuch && <ErrorMessage>{t.errors.NOT_ENOUGH_MERCHANTS}</ErrorMessage>}
        {send.isError && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
        <div className={styles.actions}>
          <Button
            type="button"
            disabled={total === 0 || tooManyMerchants || tooMuch || send.isPending}
            onClick={submit}
          >
            {send.isPending ? t.market.sending : t.market.send}
          </Button>
        </div>
        <p role="status" className={styles.sent}>
          {sent !== null && <SentText transport={sent} now={now} />}
        </p>
      </div>
    </Card>
  );
}

function SentText({ transport, now }: { transport: Transport; now: number }) {
  const { t, language } = useI18n();
  return t.market.sent(
    villageLabel(t, transport.targetVillage),
    formatWhenExact(Date.parse(transport.arriveAt), now, language),
  );
}
