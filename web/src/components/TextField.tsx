import { useId, type InputHTMLAttributes } from 'react';
import styles from './TextField.module.css';

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  /** Shown below the field while there is no error. */
  hint?: string;
  error?: string;
};

/** Label + input + hint/error, wired up for screen readers. */
export function TextField({ label, hint, error, ...inputProps }: TextFieldProps) {
  const id = useId(); // unique per component instance, stable between renders
  const messageId = `${id}-message`;
  const message = error ?? hint;

  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        className={styles.input}
        aria-invalid={error !== undefined}
        aria-describedby={message !== undefined ? messageId : undefined}
        {...inputProps}
      />
      {message !== undefined && (
        <p id={messageId} className={error !== undefined ? styles.error : styles.hint}>
          {message}
        </p>
      )}
    </div>
  );
}
