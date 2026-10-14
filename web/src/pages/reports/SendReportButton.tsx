import { useState, type FormEvent } from 'react';
import { useConversations } from '../../api/queries/messages';
import { useShareReport } from '../../api/queries/reports';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { TextField } from '../../components/TextField';
import { addShareName, MAX_SHARE_RECIPIENTS, shareNames } from '../../game/reports';
import { useT } from '../../i18n';
import styles from './ReportsPage.module.css';
import { sendError } from './reportText';

/** How many players of the inbox are offered as quick picks. */
const QUICK_PICKS = 6;

interface SendReportButtonProps {
  reportId: number;
  /** The report's title, for the button's accessible name. */
  title: string;
  /** After a send: the recipients' names as the server spells them. */
  onSent: (names: string[]) => void;
}

/**
 * "Send" on a report (M40): a sheet where the player names one or more players; each gets a copy
 * of the report in their list that says who sent it. The players of the inbox are offered as quick
 * picks, so on a phone a name is often one tap.
 */
export function SendReportButton({ reportId, title, onSent }: SendReportButtonProps) {
  const t = useT();
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        className={`${styles.delete} ${styles.sendAction}`}
        onClick={() => setOpen(true)}
        aria-label={t.reports.sendReport(title)}
        title={t.reports.send}
      >
        <Icon name="send" />
        <span>{t.reports.send}</span>
      </button>
      <Sheet open={open} onClose={() => setOpen(false)} title={t.reports.sendTitle}>
        {open && (
          <SendForm
            reportId={reportId}
            onSent={(names) => {
              onSent(names);
              setOpen(false);
            }}
          />
        )}
      </Sheet>
    </>
  );
}

function SendForm({ reportId, onSent }: { reportId: number; onSent: (names: string[]) => void }) {
  const t = useT();
  const [input, setInput] = useState('');
  const share = useShareReport(reportId);
  const conversations = useConversations();
  const names = shareNames(input);
  const tooMany = names.length > MAX_SHARE_RECIPIENTS;
  const picks = (conversations.data ?? []).slice(0, QUICK_PICKS).map((conversation) => conversation.playerName);

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (names.length === 0 || tooMany) {
      return;
    }
    share.mutate(names, { onSuccess: (result) => onSent(result.sentTo) });
  }

  return (
    <form className={styles.sendForm} onSubmit={onSubmit}>
      <p className={styles.muted}>{t.reports.sendText}</p>
      {picks.length > 0 && (
        <div className={styles.picks}>
          <span className={styles.picksLabel}>{t.reports.sendRecent}</span>
          {picks.map((name) => (
            <button
              key={name}
              type="button"
              className={styles.pick}
              onClick={() => setInput(addShareName(input, name))}
            >
              {name}
            </button>
          ))}
        </div>
      )}
      <TextField
        label={t.reports.sendNames}
        hint={t.reports.sendNamesHint}
        error={tooMany ? t.reports.sendTooMany(MAX_SHARE_RECIPIENTS) : undefined}
        value={input}
        onChange={(event) => setInput(event.target.value)}
        autoComplete="off"
        autoCapitalize="none"
        spellCheck={false}
      />
      {share.isError && <ErrorMessage>{sendError(t, share.error)}</ErrorMessage>}
      <div className={styles.sendButtons}>
        <Button type="submit" disabled={names.length === 0 || tooMany || share.isPending}>
          <Icon name="send" /> {t.reports.send}
        </Button>
      </div>
    </form>
  );
}
