/** The German texts of a push notification (see `push-en.ts`). */
import type { PushTexts } from './push-en';

export const dePushTexts: PushTexts = {
  push: {
    link: 'Benachrichtigungen',
    title: 'Benachrichtigungen',
    back: 'Zurück zum Spiel',
    intro:
      'Erhalte auf diesem Gerät eine Benachrichtigung, während das Spiel geschlossen ist, zum Beispiel sobald ein Angriff losgeschickt wird.',
    deviceTitle: 'Dieses Gerät',
    deviceOn: 'Benachrichtigungen sind für dieses Gerät an.',
    deviceOff: 'Benachrichtigungen sind für dieses Gerät aus.',
    turnOn: 'Einschalten',
    turnOff: 'Ausschalten',
    working: 'Einen Moment…',
    test: 'Test senden',
    testSent: 'Test gesendet. Er sollte in wenigen Sekunden ankommen.',
    devices: (count: number) =>
      count === 1
        ? 'Benachrichtigungen erreichen 1 deiner Geräte.'
        : `Benachrichtigungen erreichen ${count} deiner Geräte.`,
    blocked:
      'Benachrichtigungen sind für dieses Spiel in deinem Browser blockiert. Erlaube sie in den Einstellungen des Browsers für diese Seite und versuche es dann noch einmal.',
    unsupported: 'Dieser Browser kann keine Benachrichtigungen vom Spiel anzeigen.',
    iosInstall:
      'Auf iPhone und iPad gibt es Benachrichtigungen nur vom Home-Bildschirm aus: Tippe auf Teilen, dann auf „Zum Home-Bildschirm“, und öffne das Spiel von dort.',
    unavailable: 'Diese Welt sendet keine Benachrichtigungen.',
    failed: 'Benachrichtigungen ließen sich auf diesem Gerät nicht einschalten.',
    kindsTitle: 'Benachrichtigen bei',
    kinds: {
      ATTACK: 'Angriffe auf dich',
      VILLAGE_LOST: 'Verlorene Dörfer',
      MESSAGE: 'Private Nachrichten',
      GUILD_INVITE: 'Gildeneinladungen',
      WORLD_END: 'Siegesuhr und Weltende',
      BUILDS_DONE: 'Bauschleife leer',
      ATTACK_RETURNED: 'Rückkehr deiner Angriffe',
      STORAGE_FULL: 'Speicher voll',
      HELP_REQUEST: 'Bitten von Verbündeten',
    },
    kindHints: {
      ATTACK: 'Sobald ein Angriff auf eines deiner Dörfer losgeschickt wird, mit seiner Ankunft.',
      VILLAGE_LOST: 'Wenn eines deiner Dörfer erobert wird.',
      MESSAGE: 'Wenn dir ein anderer Spieler schreibt.',
      GUILD_INVITE: 'Wenn dich eine Gilde einlädt.',
      WORLD_END: 'Wenn eine Seite die Siegesuhr startet oder sie stehen bleibt, und wenn die Welt endet.',
      BUILDS_DONE: 'Wenn das letzte Gebäude in der Bauschleife eines Dorfes fertig ist.',
      ATTACK_RETURNED:
        'Wenn Angriffe, nach denen du gefragt hast, wieder zu Hause sind: ein Angriff, ein Angriffszug oder eine Farmlisten-Runde. Du wählst es beim Schicken oder mit der Glocke.',
      STORAGE_FULL:
        'Wenn Holz, Lehm oder Eisen in einem Dorf die Grenze des Speichers erreicht. Einmal, bis es wieder weniger wird.',
      HELP_REQUEST:
        'Wenn ein verbündeter Fürst dich um Verteidiger oder Rohstoffe bittet, mit der Zeit, die dir bleibt.',
    },
    testBody: 'Benachrichtigungen funktionieren auf diesem Gerät.',
    attacks: (count: number, target: string, first: string) =>
      `${count} Angriffe auf dem Weg nach ${target} – erste Ankunft ${first}`,
    villageLost: (village: string, by: string | null) =>
      by === null ? `${village} wurde erobert` : `${by} hat ${village} erobert`,
    buildsDone: (village: string, building: string, level: number) =>
      `${village}: Die Bauschleife ist leer. ${building} hat Stufe ${level} erreicht.`,
    demolishedDone: (village: string, building: string, level: number) =>
      `${village}: Die Bauschleife ist leer. ${building} wurde auf Stufe ${level} abgerissen.`,
  },
  realtime: {
    yourVillage: 'dein Dorf',
    incomingAttack: (target: string, from: string, player: string | null, arrival: string) =>
      player === null
        ? `Angriff auf ${target} von ${from} – Ankunft ${arrival}`
        : `${player} greift ${target} von ${from} aus an – Ankunft ${arrival}`,
    newReport: (kind: string) => `Neuer Bericht: ${kind}`,
    reportShared: (player: string, kind: string) => `${player} hat dir einen Bericht gesendet: ${kind}`,
    buildComplete: (village: string, building: string, level: number) =>
      `${village}: ${building} hat Stufe ${level} erreicht`,
    demolished: (village: string, building: string, level: number) =>
      `${village}: ${building} auf Stufe ${level} abgerissen`,
    incomingBadge: (count: number) =>
      count === 1 ? '1 Angriff auf dem Weg zu deinen Dörfern' : `${count} Angriffe auf dem Weg zu deinen Dörfern`,
    notifications: 'Benachrichtigungen',
    worldOpened: 'Die Welt ist eröffnet. Dein Dorf steht bereit!',
    worldEnded: (winner: string | null) =>
      winner === null ? 'Die Welt ist beendet.' : `Die Welt ist beendet: ${winner} hat gewonnen!`,
    victoryClock: (side: string, when: string) =>
      `${side} erfüllt die Siegbedingung: Sieg am ${when}, wenn niemand sie aufhält`,
    victoryClockYours: (when: string) => `Deine Gilde erfüllt die Siegbedingung: Sieg am ${when}, wenn ihr durchhaltet`,
    victoryClockYou: (when: string) => `Du erfüllst die Siegbedingung: Sieg am ${when}, wenn du durchhältst`,
    victoryClockStopped: (side: string) => `${side} erfüllt die Siegbedingung nicht mehr. Die Siegesuhr steht.`,
    victoryClockStoppedYours: 'Deine Gilde erfüllt die Siegbedingung nicht mehr. Die Siegesuhr steht.',
    victoryClockStoppedYou: 'Du erfüllst die Siegbedingung nicht mehr. Die Siegesuhr steht.',
    courtName: (key: string | null, numeral: string | null, guild: string) => {
      const names: Record<string, string> = {
        RAVEN: 'Der Rabenhof',
        LION: 'Der Löwenhof',
        EAGLE: 'Der Adlerhof',
        BEAR: 'Der Bärenhof',
        WOLF: 'Der Wolfshof',
        DRAGON: 'Der Drachenhof',
        STAG: 'Der Hirschhof',
        FALCON: 'Der Falkenhof',
        BULL: 'Der Stierhof',
        FOX: 'Der Fuchshof',
      };
      const name = key === null ? undefined : names[key];
      if (name === undefined) {
        return `Der Hof von ${guild}`;
      }
      return numeral === null ? name : `${name} ${numeral}`;
    },
    courtChanged: (court: string) => `${court} ist jetzt der stärkste Hof der Fürsten.`,
    helpRequested: (lord: string, kind: string) =>
      kind === 'RESOURCES' ? `${lord} bittet dich um Rohstoffe.` : `${lord} bittet dich um Verteidiger.`,
    guildInvited: (guild: string) => `${guild} hat dich in ihre Gilde eingeladen`,
    guildBannerRaised: (level: number) => `Deine Gilde hat ihr Banner auf Stufe ${level} angehoben`,
    messageReceived: (player: string) => `Neue Nachricht von ${player}`,
    achievementEarned: (name: string, level: string | null) =>
      level === null ? `Erfolg erreicht: ${name}` : `Erfolg erreicht: ${name} (${level})`,
    bannerFound: (banner: string, village: string) => `Banner gefunden bei ${village}: ${banner}`,
    bannerLost: (banner: string, village: string) => `Banner aus ${village} gestohlen: ${banner}`,
    bannerStolen: (banner: string, village: string) =>
      `Deine Diebe haben in ${village} ein Banner gestohlen: ${banner}`,
    dismiss: 'Ausblenden',
    troopsBack: (village: string, from: string, loot: string | null) =>
      loot === null
        ? `Deine Truppen aus ${from} sind zurück in ${village}.`
        : `Deine Truppen aus ${from} sind zurück in ${village}, mit ${loot} Rohstoffen.`,
    nobodyBack: (from: string) => `Aus ${from} ist niemand zurückgekehrt.`,
    attacksBack: (count: number, village: string, from: string | null, loot: string | null, lost: number) => {
      if (lost === count) {
        const ofThem = count === 1 ? 'Von dem Angriff' : `Von den ${count} Angriffen`;
        return from === null
          ? `Farmlisten-Runde aus ${village}: ${ofThem} ist niemand zurückgekehrt.`
          : `${ofThem} auf ${from} ist niemand zurückgekehrt.`;
      }
      const attacks = count === 1 ? '1 Angriff' : `${count} Angriffe`;
      const head =
        from === null
          ? `Farmlisten-Runde zurück in ${village}: ${attacks}`
          : `${attacks} auf ${from} zurück in ${village}`;
      return `${head}${loot === null ? '' : `, ${loot} Rohstoffe`}${lost === 0 ? '' : `, ${lost} verloren`}.`;
    },
    storageFull: (village: string, resources: string) => `Speicher voll in ${village}: ${resources}.`,
  },
  resources: {
    title: 'Rohstoffe',
    wood: 'Holz',
    clay: 'Lehm',
    iron: 'Eisen',
    population: 'Bevölkerung',
    perHour: (amount: string) => `+${amount}/h`,
    perHourLong: (amount: string) => `${amount} pro Stunde`,
    capacity: (amount: string) => `max. ${amount}`,
    full: 'Speicher voll',
    populationFull: 'Bauernhof voll',
    populationAlmostFull: 'Bauernhof fast voll',
    showDetails: 'Rohstoff-Details anzeigen',
    hideDetails: 'Rohstoff-Details ausblenden',
    toWarehouse: 'Speicher öffnen',
  },
  buildings: {
    HEADQUARTERS: 'Rathaus',
    BARRACKS: 'Kaserne',
    STABLE: 'Stall',
    WORKSHOP: 'Werkstatt',
    ACADEMY: 'Akademie',
    SMITHY: 'Schmiede',
    RALLY_POINT: 'Sammelplatz',
    MARKET: 'Marktplatz',
    WOODCUTTER: 'Holzfäller',
    CLAY_PIT: 'Lehmgrube',
    IRON_MINE: 'Eisenmine',
    FARM: 'Bauernhof',
    WAREHOUSE: 'Speicher',
    HIDING_PLACE: 'Versteck',
    WALL: 'Wall',
    BANNER_HALL: 'Bannerhalle',
  },
  map: {
    barbarianVillage: 'Barbarendorf',
  },
};
