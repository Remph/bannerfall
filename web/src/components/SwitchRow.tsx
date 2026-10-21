import styles from './SwitchRow.module.css';

interface SwitchRowProps {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}

/** A quiet row with a sliding on/off switch at the right (1.6); tapping the text toggles it too. */
export function SwitchRow({ label, checked, disabled = false, onChange }: SwitchRowProps) {
  return (
    <label className={styles.row}>
      <span className={styles.label}>{label}</span>
      <input
        type="checkbox"
        role="switch"
        className={styles.switch}
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
      />
    </label>
  );
}
