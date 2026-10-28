import { useId, useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { useDonateToBanner, type GuildBanner } from '../../api/queries/guilds';
import { useVillage } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { UnitCountInput } from '../../components/UnitCountInput';
import { formatNumber } from '../../game/format';
import { projectResources, resourceTypes, type ResourceAmounts } from '../../game/resources';
import { useGameNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { useCurrentVillageId } from '../../layout/useCurrentVillageId';
import styles from './Guild.module.css';

const NOTHING: ResourceAmounts = { wood: 0, clay: 0, iron: 0 };

/**
 * The sheet of "Donate" (1.7): pick one of the player's villages, enter wood, clay and iron (one to
 * one towards the fund, together at most what the next level still lacks). Its content lives in
 * its own component so the amounts start empty every time the sheet opens.
 */
export function GuildBannerDonate({
  banner,
  open,
  onClose,
}: {
  banner: GuildBanner;
  open: boolean;
  onClose: () => void;
}) {
  const { t } = useI18n();
  return (
    <Sheet open={open} onClose={onClose} title={t.guild.banner.donateTitle}>
      <DonateForm banner={banner} onClose={onClose} />
    </Sheet>
  );
}

function DonateForm({ banner, onClose }: { banner: GuildBanner; onClose: () => void }) {
  const { t, language } = useI18n();
  const labels = t.guild.banner;
  const me = useCurrentAccount();
  const currentVillageId = useCurrentVillageId();
  const selectId = useId();
  const [villageId, setVillageId] = useState<number | undefined>(currentVillageId);
  const [amounts, setAmounts] = useState<ResourceAmounts>(NOTHING);
  const donate = useDonateToBanner();
  const village = useVillage(villageId);
  const now = useGameNow();

  const stock = village.data === undefined ? NOTHING : projectResources(village.data, now);
  const total = amounts.wood + amounts.clay + amounts.iron;
  const format = (amount: number) => formatNumber(amount, language);

  function change(type: keyof ResourceAmounts, value: number) {
    setAmounts((previous) => ({ ...previous, [type]: value }));
    donate.reset();
  }

  function submit() {
    if (villageId === undefined || total <= 0) {
      return;
    }
    donate.mutate({ villageId, ...amounts }, { onSuccess: onClose });
  }

  return (
    <div className={styles.donate}>
      <p className={styles.hint}>{labels.donateHint(format(banner.missing))}</p>
      {me.villages.length > 1 && (
        <div className={styles.pick}>
          <label htmlFor={selectId}>{labels.village}</label>
          <select
            id={selectId}
            value={villageId ?? ''}
            onChange={(event) => {
              setVillageId(Number(event.target.value));
              setAmounts(NOTHING);
              donate.reset();
            }}
          >
            {me.villages.map((own) => (
              <option key={own.id} value={own.id}>
                {own.name}
              </option>
            ))}
          </select>
        </div>
      )}
      <ul className={styles.amounts}>
        {resourceTypes.map((type) => {
          const others = total - amounts[type];
          // a donation can't be larger than the stock of the village or than what the fund lacks
          const max = Math.max(0, Math.min(Math.floor(stock[type]), banner.missing - others));
          return (
            <li key={type} className={styles.amountRow}>
              <span className={styles.amountName}>
                <Icon name={type} />
                <span>
                  {t.resources[type]}
                  <span className={styles.rowSub}> {labels.stock(format(stock[type]))}</span>
                </span>
              </span>
              <UnitCountInput
                value={amounts[type]}
                onChange={(value) => change(type, value)}
                max={max}
                limit={Math.max(0, banner.missing)}
                step={100}
                label={labels.amountLabel(t.resources[type])}
              />
            </li>
          );
        })}
      </ul>
      {donate.isError && <ErrorMessage>{errorMessage(t, donate.error)}</ErrorMessage>}
      <div className={styles.buttons}>
        <Button type="button" variant="secondary" onClick={onClose}>
          {t.common.cancel}
        </Button>
        <Button type="button" disabled={donate.isPending || total <= 0 || villageId === undefined} onClick={submit}>
          {total > 0 ? labels.total(format(total)) : labels.donate}
        </Button>
      </div>
    </div>
  );
}
