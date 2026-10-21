import { useId, useState } from 'react';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Sheet } from '../../components/Sheet';
import type { PlanBuilding, PlanLine } from '../../game/buildPlans';
import { decodePlan, encodePlan } from '../../game/planCode';
import { useT } from '../../i18n';
import styles from './AutomationPage.module.css';

export type CodeMode = 'share' | 'import';

interface PlanCodeSheetProps {
  /** Closed while undefined. */
  mode: CodeMode | undefined;
  /** The editor's plan, for sharing. */
  name: string;
  lines: readonly PlanLine[];
  buildings: readonly PlanBuilding[];
  /** The pasted plan checked and accepted: the editor takes it over (nothing is saved yet). */
  onApply: (name: string, lines: readonly PlanLine[]) => void;
  onClose: () => void;
}

/**
 * Share a plan as a code (1.6.1) or paste one: the same text works in any world, since it holds
 * only building names and levels. A pasted code is checked like a typed plan (`decodePlan`) before
 * the editor takes it over.
 */
export function PlanCodeSheet({ mode, name, lines, buildings, onApply, onClose }: PlanCodeSheetProps) {
  const t = useT();
  return (
    <Sheet
      open={mode !== undefined}
      onClose={onClose}
      title={mode === 'import' ? t.automation.code.importTitle : t.automation.code.shareTitle}
    >
      {mode === 'share' && <ShareBody name={name} lines={lines} />}
      {mode === 'import' && <ImportBody buildings={buildings} onApply={onApply} />}
    </Sheet>
  );
}

function ShareBody({ name, lines }: { name: string; lines: readonly PlanLine[] }) {
  const t = useT();
  const fieldId = useId();
  const [copied, setCopied] = useState<'yes' | 'failed' | undefined>();
  const code = encodePlan(name, lines);
  async function copy() {
    try {
      await navigator.clipboard.writeText(code);
      setCopied('yes');
    } catch {
      setCopied('failed'); // no clipboard access: the text is selectable
    }
  }
  return (
    <div className={styles.codeBody}>
      <p className={styles.muted}>{t.automation.code.shareHint}</p>
      <label htmlFor={fieldId} className="sr-only">
        {t.automation.code.label}
      </label>
      <textarea
        id={fieldId}
        className={styles.codeField}
        readOnly
        rows={5}
        value={code}
        onFocus={(event) => event.currentTarget.select()}
      />
      <Button type="button" onClick={() => void copy()}>
        {t.automation.code.copy}
      </Button>
      {copied === 'yes' && <p role="status">{t.automation.code.copied}</p>}
      {copied === 'failed' && <ErrorMessage>{t.automation.code.copyFailed}</ErrorMessage>}
    </div>
  );
}

function ImportBody({
  buildings,
  onApply,
}: {
  buildings: readonly PlanBuilding[];
  onApply: (name: string, lines: readonly PlanLine[]) => void;
}) {
  const t = useT();
  const fieldId = useId();
  const [text, setText] = useState('');
  const [error, setError] = useState<string | undefined>();
  function apply() {
    const result = decodePlan(text, buildings);
    if (result.ok) {
      onApply(result.name, result.lines);
    } else {
      setError(t.automation.code.errors[result.error]);
    }
  }
  return (
    <div className={styles.codeBody}>
      <p className={styles.muted}>{t.automation.code.importHint}</p>
      <label htmlFor={fieldId} className="sr-only">
        {t.automation.code.label}
      </label>
      <textarea
        id={fieldId}
        className={styles.codeField}
        rows={5}
        value={text}
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        placeholder="BFP1|…"
        onChange={(event) => {
          setText(event.target.value);
          setError(undefined);
        }}
      />
      {error !== undefined && <ErrorMessage>{error}</ErrorMessage>}
      <Button type="button" disabled={text.trim() === ''} onClick={apply}>
        {t.automation.code.apply}
      </Button>
    </div>
  );
}
