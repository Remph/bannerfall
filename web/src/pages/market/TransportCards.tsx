import type { Transport } from '../../api/queries/market';
import { Card } from '../../components/Card';
import { CostList } from '../../components/CostList';
import { Countdown } from '../../components/Countdown';
import { Icon } from '../../components/Icon';
import { formatNumber, formatWhenExact } from '../../game/format';
import { useServerNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { VillageRefText } from '../rally/VillageRefText';
import styles from './MarketPage.module.css';

interface TransportCardProps {
  villageId: number;
  transports: readonly Transport[];
}

/**
 * The merchants this village sent: on the way (arrival countdown) or already delivered and walking
 * home (countdown to when they are free again).
 */
export function OutgoingTransportsCard({ villageId, transports }: TransportCardProps) {
  const { t } = useI18n();
  return (
    <Card title={t.market.outgoingTitle}>
      {transports.length === 0 ? (
        <p className={styles.empty}>{t.market.noOutgoing}</p>
      ) : (
        <ul className={styles.moveList}>
          {transports.map((transport) => (
            <TransportLine key={transport.id} villageId={villageId} transport={transport} outgoing />
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Merchants on their way to this village, next first. */
export function IncomingTransportsCard({ villageId, transports }: TransportCardProps) {
  const { t } = useI18n();
  return (
    <Card title={t.market.incomingTitle}>
      {transports.length === 0 ? (
        <p className={styles.empty}>{t.market.noIncoming}</p>
      ) : (
        <ul className={styles.moveList}>
          {transports.map((transport) => (
            <TransportLine key={transport.id} villageId={villageId} transport={transport} outgoing={false} />
          ))}
        </ul>
      )}
    </Card>
  );
}

function TransportLine({
  villageId,
  transport,
  outgoing,
}: {
  villageId: number;
  transport: Transport;
  outgoing: boolean;
}) {
  const { t, language } = useI18n();
  const now = useServerNow();
  const other = outgoing ? transport.targetVillage : transport.sourceVillage;
  return (
    <li className={styles.move}>
      <span className={styles.moveIcon}>
        <Icon name="MARKET" />
      </span>
      <span className={styles.moveText}>
        <span className={styles.moveTitle}>
          {outgoing ? t.market.merchantsTo : t.market.merchantsFrom}{' '}
          <VillageRefText village={other} currentVillageId={villageId} />
          {other.playerName !== null && <span className={styles.player}> · {other.playerName}</span>}
        </span>
        <CostList label={t.market.goods} cost={transport.resources} hideZero />
        <span className={styles.moveTime}>
          {transport.arrived ? (
            <>
              {t.market.delivered} · {t.market.homeIn}{' '}
              <Countdown until={transport.returnAt} className={styles.countdown} />
            </>
          ) : (
            <>
              <Countdown until={transport.arriveAt} className={styles.countdown} />
              {' · '}
              {t.rally.arrivesAt(formatWhenExact(Date.parse(transport.arriveAt), now, language))}
            </>
          )}
          {' · '}
          {t.market.merchantCount(formatNumber(transport.merchants, language), transport.merchants === 1)}
        </span>
      </span>
    </li>
  );
}
