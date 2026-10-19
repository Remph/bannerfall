import { useAuthOptions, useMe } from '../../api/queries/auth';
import { useWorldOperator, type WorldOperator } from '../../api/queries/world';
import { BackLink } from '../../components/BackLink';
import { useT } from '../../i18n';
import { isEmailAddress } from './contact';
import styles from './PrivacyPage.module.css';

/**
 * /privacy: what the world stores and why (05-frontend §3). Open to guests (linked under the login
 * and register forms) and to players (settings menu). The host's name and contact are settings of
 * the server; without them the page shows only the data part.
 */
export function PrivacyPage() {
  const t = useT();
  const privacy = t.privacy;
  const loggedIn = (useMe().data ?? null) !== null;
  const operator = useWorldOperator().data;
  const options = useAuthOptions().data;
  const accountServer =
    options !== undefined && options.mode !== 'OFF' && options.accountServerName !== null
      ? options.accountServerName
      : null;
  const sections: readonly (readonly [string, string])[] = [
    [privacy.accountTitle, privacy.account],
    // only on a world that has one (1.5)
    ...(accountServer === null
      ? []
      : ([
          [
            privacy.accountServerTitle,
            options?.careerReports === true
              ? `${privacy.accountServer(accountServer)} ${privacy.accountServerCareer(accountServer)}`
              : privacy.accountServer(accountServer),
          ],
        ] as const)),
    [privacy.gameTitle, privacy.game],
    [privacy.messagesTitle, privacy.messages],
    [privacy.pushTitle, privacy.push],
    [privacy.addressTitle, privacy.address],
    [privacy.logsTitle, privacy.logs],
    [privacy.cookiesTitle, privacy.cookies],
    [privacy.purposeTitle, privacy.purpose],
    [privacy.rightsTitle, privacy.rights],
  ];
  return (
    <article className={styles.page}>
      <BackLink to={loggedIn ? '/' : '/login'}>{loggedIn ? privacy.backToGame : privacy.backToLogin}</BackLink>
      <h1 className={styles.title}>{privacy.title}</h1>
      <p className={styles.text}>{privacy.intro}</p>
      {operator !== undefined && (operator.name !== null || operator.contact !== null) && (
        <OperatorSection operator={operator} />
      )}
      {sections.map(([title, text]) => (
        <section key={title} className={styles.section}>
          <h2 className={styles.heading}>{title}</h2>
          <p className={styles.text}>{text}</p>
        </section>
      ))}
    </article>
  );
}

function OperatorSection({ operator }: { operator: WorldOperator }) {
  const t = useT();
  const { name, contact } = operator;
  return (
    <section className={`${styles.section} ${styles.operator}`}>
      <h2 className={styles.heading}>{t.privacy.operatorTitle}</h2>
      <p className={styles.text}>{t.privacy.operatorText}</p>
      <address className={styles.address}>
        {name !== null && <span>{name}</span>}
        {contact !== null &&
          (isEmailAddress(contact) ? <a href={`mailto:${contact}`}>{contact}</a> : <span>{contact}</span>)}
      </address>
    </section>
  );
}
