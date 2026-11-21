/**
 * Die Briefe der Fürsten auf Deutsch (2.1 S2b, S3 und S4, 12-solo-and-allies §5, §8, §9), in drei
 * Stimmen, eine je Wesen: STEADY (schlicht und höflich), CAREFUL (warm, besorgt, dankbar) und BOLD
 * (kurz, stolz, derb). Die Stimme zeigt, wer ein Fürst ist, ohne sein Wesen zu nennen (09 §2). Ein
 * Brief von vor S4 hat keine Stimme und liest sich in der ruhigen. `de.ts` nimmt sie von hier.
 */
import type { LetterParts, LordLetter } from '../game/letters';
import { opt, type LetterKindTexts } from './letters-en';

/** "300 Verteidiger" im Satz. */
const defenders = (p: LetterParts) => (p.count === '' ? 'Verteidiger' : `${p.count} Verteidiger`);

/** "Das Heer von Gräfin Irmgard", ohne Angreifer "Ein Heer". */
const army = (p: LetterParts) => (p.attacker === '' ? 'Ein Heer' : `Das Heer von ${p.attacker}`);

const goal = (p: LetterParts) => (p.goal === 'WALL' ? 'Mein Wall' : 'Mein nächster Adliger');

const steady: LetterKindTexts = {
  JOINED: (p) =>
    `Ich nehme an. Von heute an weht mein Banner für ${p.guild}. Gib Bescheid, wenn du meine Schwerter brauchst.`,
  REFUSED_TRUST: () =>
    'Dein Angebot ehrt mich, aber ich kenne dich noch nicht gut genug. Steh mir erst bei, dann frag wieder.',
  REFUSED_FULL: (p) => `In ${p.guild} ist kein Platz mehr für mich. Mach Platz, dann frag wieder.`,
  LEFT_TRUST: (p) => `Ich traue dir nicht mehr. Mein Banner verlässt ${p.guild} noch heute.`,
  LEFT_ATTACKED: (p) =>
    `Du hast deine Truppen gegen mich geschickt. Ich verlasse ${p.guild} noch in dieser Stunde, und ich vergesse es nicht.`,
  ORDER_ATTACK: (p) =>
    `Ich bin auf dem Weg nach ${p.target}${opt(p.troops, ` mit ${p.troops}`)}.${opt(p.arrive, ` Wir schlagen um ${p.arrive} ein.`)}`,
  ORDER_ATTACK_PLANNED: (p) =>
    `Wir schlagen gemeinsam bei ${p.target} ein. Ich breche${opt(p.depart, ` um ${p.depart}`)}${opt(p.troops, ` mit ${p.troops}`)} auf${opt(p.arrive, ` und lande um ${p.arrive}`)}, kurz nach dir.`,
  ORDER_SUPPORT: (p) =>
    `Meine Leute stehen dir in ${p.target} bei${opt(p.troops, `: ${p.troops}`)}${opt(p.arrive, `, dort um ${p.arrive}`)}. Sie bleiben, solange du sie brauchst.`,
  ORDER_SEND: (p) =>
    `Meine Händler sind${opt(p.goods, ` mit ${p.goods}`)} unterwegs nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}.`,
  ORDER_HOLD: (p) => `Wie du willst. Ich lasse ${p.target} in Ruhe, bis du etwas anderes sagst.`,
  ORDER_REFUSED_TRUST: (p) =>
    `Nein. Ich traue dir nicht genug, um auf dein Wort zu marschieren. ${p.target} muss warten.`,
  ORDER_REFUSED_BOUND: (p) => `Nein. ${p.target} gehört den Meinen, und ich bin an sie gebunden.`,
  ORDER_TOO_STRONG: (p) => `Nicht genug Truppen: Bei ${p.target} würde ich verlieren. Ich werfe meine Leute nicht weg.`,
  ORDER_NO_TROOPS: (p) => `Für ${p.target} kann ich gerade keine Verteidiger entbehren.`,
  ORDER_NO_SURPLUS: (p) =>
    `Nach ${p.target} kann ich nichts schicken: Was ich habe, brauche ich für meinen eigenen Ausbau.`,
  ORDER_NO_ROOM: (p) => `Deine Speicher in ${p.target} sind voll. Meine Händler trügen es umsonst dorthin.`,
  ORDER_NOT_IN_TIME: (p) =>
    `Ich schaffe es nicht rechtzeitig. Dein Angriff auf ${p.target} schlägt ein, bevor meine Leute dazukommen.`,
  ORDER_UNSEEN: (p) =>
    `Ich weiß nicht, was in ${p.target} steht, und keiner meiner Späher hat es mir gesagt. Blind marschiere ich nicht.`,
  ORDER_CAPS: (p) =>
    `${p.target} hat von meinen Leuten fürs Erste genug gelitten. Frag mich an einem anderen Tag wieder.`,
  ORDER_PROTECTED: (p) => `${p.target} steht unter Schutz. Noch darf kein Heer es anrühren.`,
  HELP_SUPPORT: (p) =>
    `${army(p)} ist auf dem Weg nach ${p.target}. Schick mir ${defenders(p)}${opt(p.arrive, `, bevor es um ${p.arrive} einschlägt`)}, und ich vergesse es dir nicht.`,
  HELP_RESOURCES: (p) =>
    `${goal(p)} in ${p.target} wartet schon zu lange. Kannst du mir${opt(p.arrive, ` bis ${p.arrive}`)} ${p.goods || 'Rohstoffe'} schicken?`,
  THANKS: (p) =>
    p.goods === ''
      ? 'Du bist gekommen, als ich dich rief. Das merke ich mir.'
      : `Du bist gekommen, als ich dich rief. Zum Dank bringen meine Händler ${p.goods} nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}.`,
  WARNING: (p) =>
    `Wir hören wenig voneinander in letzter Zeit. Ein Bündnis, das man nicht pflegt, verblasst, und ${p.guild} ist da nicht anders.`,
  COURT_TAUNT: (p) =>
    `${p.court} hält jetzt das Land. ${p.arrive === '' ? 'Bald' : `Um ${p.arrive}`} gehört es uns, wenn ihr uns nicht aufhaltet.`,
};

const careful: LetterKindTexts = {
  JOINED: (p) => `Danke für dein Vertrauen. Gern trete ich ${p.guild} bei; mögen wir einander schützen.`,
  REFUSED_TRUST: () =>
    'Verzeih, ich bin ein vorsichtiger Mensch. Ich kenne dich kaum. Hilf mir in einer schweren Stunde, dann sage ich gern Ja.',
  REFUSED_FULL: (p) => `Ich würde wirklich gern, aber ${p.guild} ist voll. Schreib mir, wenn wieder Platz ist.`,
  LEFT_TRUST: (p) =>
    `Es schmerzt mich, aber ich kann mich nicht mehr auf dich verlassen. Ich verlasse ${p.guild}. Leb wohl.`,
  LEFT_ATTACKED: (p) =>
    `Du hast mich angegriffen, deinen eigenen Verbündeten. Ich verlasse ${p.guild} sofort. Wie konntest du nur?`,
  ORDER_ATTACK: (p) =>
    `Ich gehe, auch wenn mir das Herz schwer ist. ${p.troops || 'Meine Leute'} ziehen nach ${p.target}${opt(p.arrive, ` und schlagen um ${p.arrive} ein`)}.`,
  ORDER_ATTACK_PLANNED: (p) =>
    `Gut, wir schlagen gemeinsam bei ${p.target} zu. Ich breche${opt(p.depart, ` um ${p.depart}`)}${opt(p.troops, ` mit ${p.troops}`)} auf${opt(p.arrive, ` und lande um ${p.arrive}`)}, gleich nach dir.`,
  ORDER_SUPPORT: (p) =>
    `Natürlich helfe ich. ${p.troops || 'Meine Leute'} sind unterwegs nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}. Behalte sie, solange du sie brauchst.`,
  ORDER_SEND: (p) =>
    `Gern. ${p.goods || 'Meine Händler'} sind unterwegs nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}.`,
  ORDER_HOLD: (p) => `Ich lasse ${p.target} in Frieden, wie du es wünschst.`,
  ORDER_REFUSED_TRUST: (p) =>
    `Verzeih, aber ich kenne dich noch nicht gut genug, um meine Leute auf dein Wort nach ${p.target} zu schicken.`,
  ORDER_REFUSED_BOUND: (p) =>
    `Ich kann nicht. ${p.target} gehört Freunden von mir, und ich habe ihnen mein Wort gegeben.`,
  ORDER_TOO_STRONG: (p) => `Ich fürchte, ${p.target} ist zu stark für mich. Ich würde dort nur gute Leute verlieren.`,
  ORDER_NO_TROOPS: (p) => `Es tut mir leid, ich brauche jeden Verteidiger. Keiner kann jetzt nach ${p.target}.`,
  ORDER_NO_SURPLUS: (p) =>
    `Es tut mir leid, für ${p.target} habe ich nichts übrig. Jeder Stein geht in meine eigenen Mauern.`,
  ORDER_NO_ROOM: (p) => `Deine Speicher in ${p.target} sind schon voll. Ich würde es nur verschwenden.`,
  ORDER_NOT_IN_TIME: (p) => `Ich erreiche ${p.target} nicht rechtzeitig, um mit dir einzuschlagen. Verzeih mir.`,
  ORDER_UNSEEN: (p) => `Ich sehe nicht, was in ${p.target} wartet. Blind wage ich meine Leute nicht hinzuschicken.`,
  ORDER_CAPS: (p) => `Ich habe ${p.target} für heute genug bedrängt. Frag mich morgen wieder.`,
  ORDER_PROTECTED: (p) => `${p.target} steht noch unter Schutz. Niemand darf es schon angreifen.`,
  HELP_SUPPORT: (p) =>
    `Bitte, ich brauche dich. ${army(p)} zieht nach ${p.target}${opt(p.arrive, ` und schlägt um ${p.arrive} ein`)}. Allein halte ich nicht stand: Schick ${defenders(p)}, ich bitte dich.`,
  HELP_RESOURCES: (p) =>
    `${goal(p)} in ${p.target} kommt nicht voran, und das macht mir Sorgen. Kannst du mir${opt(p.arrive, ` bis ${p.arrive}`)} ${p.goods || 'etwas Rohstoffe'} überlassen?`,
  THANKS: (p) =>
    p.goods === ''
      ? 'Du warst da, als ich dich am meisten brauchte. Danke, von ganzem Herzen.'
      : `Du warst da, als ich dich am meisten brauchte. Danke, von ganzem Herzen. ${p.goods} sind unterwegs nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}.`,
  WARNING: (p) =>
    `Ich habe lange nichts von dir gehört, und das macht mir Sorgen. Ich fürchte, ${p.guild} bedeutet dir weniger als früher.`,
  COURT_TAUNT: (p) =>
    `Es tut mir leid, dass es so weit kommt. ${p.court} hat das Land in der Hand, und ${p.arrive === '' ? 'bald' : `um ${p.arrive}`} gehört es uns.`,
};

const bold: LetterKindTexts = {
  JOINED: (p) => `Abgemacht. Mein Banner weht für ${p.guild}. Zeig mir deine Feinde.`,
  REFUSED_TRUST: () => 'Nein. Zeig mir erst auf dem Feld, was du wert bist.',
  REFUSED_FULL: (p) => `${p.guild} ist voll. Wirf jemanden raus, dann frag.`,
  LEFT_TRUST: (p) => `Du bist mein Banner nicht wert. Ich verlasse ${p.guild}.`,
  LEFT_ATTACKED: (p) => `Du wagst es, mich anzugreifen. Ich verlasse ${p.guild}, und nächstes Mal komme ich mit Äxten.`,
  ORDER_ATTACK: (p) =>
    `Mit Vergnügen. ${p.troops || 'Meine Leute'} auf ${p.target}${opt(p.arrive, `, Einschlag um ${p.arrive}`)}.`,
  ORDER_ATTACK_PLANNED: (p) =>
    `Wir schlagen ${p.target} gemeinsam. Ich breche${opt(p.depart, ` um ${p.depart}`)}${opt(p.troops, ` mit ${p.troops}`)} auf${opt(p.arrive, `, Einschlag um ${p.arrive}`)}. Sei pünktlich.`,
  ORDER_SUPPORT: (p) =>
    `${p.troops || 'Meine Leute'} nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}. Verschwende sie nicht.`,
  ORDER_SEND: (p) => `${p.goods || 'Ware'} nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}. Mach was draus.`,
  ORDER_HOLD: (p) => `Na gut. ${p.target} lebt, vorerst.`,
  ORDER_REFUSED_TRUST: (p) => `Nein. Für jemanden, dem ich nicht traue, marschiere ich nicht nach ${p.target}.`,
  ORDER_REFUSED_BOUND: (p) => `Nein. ${p.target} gehört zu den Meinen.`,
  ORDER_TOO_STRONG: (p) => `${p.target} würde mich heute schlagen. Ich verliere keine Schlacht mit Absicht.`,
  ORDER_NO_TROOPS: (p) => `Meine Leute haben zu tun. Keiner für ${p.target}.`,
  ORDER_NO_SURPLUS: (p) => `Nichts übrig für ${p.target}. Ich baue zuerst.`,
  ORDER_NO_ROOM: (p) => `${p.target} ist bis unters Dach voll. Gib es erst aus.`,
  ORDER_NOT_IN_TIME: (p) => `Zu weit. Mit dir zusammen schaffe ich ${p.target} nicht.`,
  ORDER_UNSEEN: (p) => `Ich weiß nicht, was in ${p.target} steht. Blind marschiere ich nicht.`,
  ORDER_CAPS: (p) => `${p.target} hat heute genug von mir. Morgen.`,
  ORDER_PROTECTED: (p) => `${p.target} steht unter Schutz. Noch nicht.`,
  HELP_SUPPORT: (p) =>
    `${p.attacker || 'Jemand'} hält ${p.target} für leichte Beute. Beweis das Gegenteil: ${defenders(p)}${opt(p.arrive, ` bis ${p.arrive}`)}.`,
  HELP_RESOURCES: (p) =>
    `${goal(p)} in ${p.target} steckt fest. Ich brauche ${p.goods || 'Rohstoffe'}${opt(p.arrive, ` bis ${p.arrive}`)}.`,
  THANKS: (p) =>
    p.goods === ''
      ? 'Du bist gekommen. Gut. Das vergesse ich nicht.'
      : `Du bist gekommen. Gut. ${p.goods} sind unterwegs nach ${p.target}${opt(p.arrive, `, dort um ${p.arrive}`)}.`,
  WARNING: (p) => `Du bist still geworden. Ein Verbündeter, der nichts tut, ist keiner. Denk an ${p.guild}.`,
  COURT_TAUNT: (p) =>
    `${p.court} nimmt das Land. ${p.arrive === '' ? 'Bald' : `Um ${p.arrive}`} gehört es uns. Halt uns doch auf.`,
};

export const deLetters: Record<NonNullable<LordLetter['voice']>, LetterKindTexts> = {
  STEADY: steady,
  CAREFUL: careful,
  BOLD: bold,
};
