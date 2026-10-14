import { useI18n } from '../i18n';
import { Button } from './Button';

/** Switches between English and German. The label is the other language's own name. */
export function LanguageToggle() {
  const { language, setLanguage, t } = useI18n();
  const other = language === 'en' ? 'de' : 'en';

  return (
    <Button
      type="button"
      variant="secondary"
      lang={other}
      title={t.common.switchLanguage}
      onClick={() => setLanguage(other)}
    >
      {t.common.otherLanguage}
    </Button>
  );
}
