/**
 * The lords' letters in English (2.1 S2b, S3 and S4, 12-solo-and-allies §5, §8, §9), in three voices,
 * one per personality: STEADY (plain and courteous), CAREFUL (warm, worried, grateful) and BOLD
 * (short, proud, blunt). The voice shows who a lord is without naming its personality (09 §2). A
 * letter written before S4 has no voice and reads in the steady one. `en.ts` takes them from here.
 */
import type { LetterParts, LordLetter } from '../game/letters';

type Write = (p: LetterParts) => string;

/** One voice: a text for every kind of letter. */
export type LetterKindTexts = Record<LordLetter['kind'], Write>;

/**
 * `text` when its fact is there, else nothing: a letter whose fact is missing leaves the clause out
 * (UI review 2.1, finding 13; the help sheet leaves the time out on purpose, finding 06).
 */
export const opt = (fact: string, text: string): string => (fact === '' ? '' : text);

/** "300 defenders" in a sentence. */
const defenders = (p: LetterParts) => (p.count === '' ? 'defenders' : `${p.count} defenders`);

/** "Gräfin Irmgard’s army", or "An army" without the attacker. */
const army = (p: LetterParts) => (p.attacker === '' ? 'An army' : `${p.attacker}’s army`);

const steady: LetterKindTexts = {
  JOINED: (p) => `I accept. From this day my banner flies with ${p.guild}. Send word when you need my swords.`,
  REFUSED_TRUST: () =>
    'Your offer honours me, but I do not know you well enough yet. Stand by me first, then ask again.',
  REFUSED_FULL: (p) => `${p.guild} has no place left for me. Make room, then ask again.`,
  LEFT_TRUST: (p) => `I no longer trust you. My banner leaves ${p.guild} today.`,
  LEFT_ATTACKED: (p) => `You sent your troops against me. I leave ${p.guild} this very hour, and I will not forget it.`,
  // orders (2.1 S3, 12-solo-and-allies §8): the lord's answer, with the facts the server sent
  ORDER_ATTACK: (p) =>
    `On my way to ${p.target}${opt(p.troops, ` with ${p.troops}`)}.${opt(p.arrive, ` We land at ${p.arrive}.`)}`,
  ORDER_ATTACK_PLANNED: (p) =>
    `We land together at ${p.target}. I march${opt(p.depart, ` at ${p.depart}`)}${opt(p.troops, ` with ${p.troops}`)}${opt(p.arrive, ` and land at ${p.arrive}`)}, just after you.`,
  ORDER_SUPPORT: (p) =>
    `My men stand by you at ${p.target}${opt(p.troops, `: ${p.troops}`)}${opt(p.arrive, `, there at ${p.arrive}`)}. They stay while you need them.`,
  ORDER_SEND: (p) =>
    `My merchants are on their way to ${p.target}${opt(p.goods, ` with ${p.goods}`)}${opt(p.arrive, `, there at ${p.arrive}`)}.`,
  ORDER_HOLD: (p) => `As you wish. I leave ${p.target} alone until you say otherwise.`,
  ORDER_REFUSED_TRUST: (p) => `No. I do not trust you enough to march on your word. ${p.target} will have to wait.`,
  ORDER_REFUSED_BOUND: (p) => `No. ${p.target} belongs to my own, and I am bound to them.`,
  ORDER_TOO_STRONG: (p) => `Not enough troops: I would lose at ${p.target}. I will not throw my men away.`,
  ORDER_NO_TROOPS: (p) => `I have no defenders to spare for ${p.target} right now.`,
  ORDER_NO_SURPLUS: (p) => `I cannot send anything to ${p.target}: what I hold I need for my own building.`,
  ORDER_NO_ROOM: (p) => `Your storehouses in ${p.target} are full. My merchants would carry it there for nothing.`,
  ORDER_NOT_IN_TIME: (p) => `I can't be there in time. Your attack on ${p.target} lands before my men could join it.`,
  ORDER_UNSEEN: (p) =>
    `I do not know what stands in ${p.target}, and no scout of mine could tell me. I will not march blind.`,
  ORDER_CAPS: (p) => `${p.target} has suffered enough from my men for now. Ask me again another day.`,
  ORDER_PROTECTED: (p) => `${p.target} is under protection. No army may touch it yet.`,
  // help requests and what follows them (2.1 S4, 12-solo-and-allies §9)
  HELP_SUPPORT: (p) =>
    `${army(p)} is on the road to ${p.target}. Send me ${defenders(p)}${opt(p.arrive, ` before it lands at ${p.arrive}`)}, and I will not forget it.`,
  HELP_RESOURCES: (p) =>
    `${p.goal === 'WALL' ? 'My wall' : 'My next noble'} at ${p.target} has waited too long. Could you send ${p.goods || 'resources'}${opt(p.arrive, ` before ${p.arrive}`)}?`,
  THANKS: (p) =>
    p.goods === ''
      ? 'You came when I asked. I will remember it.'
      : `You came when I asked. My merchants bring ${p.goods} to ${p.target} as thanks${opt(p.arrive, `, there at ${p.arrive}`)}.`,
  WARNING: (p) =>
    `We hear little of each other lately. An alliance that is not kept fades, and ${p.guild} is no different.`,
  COURT_TAUNT: (p) =>
    `${p.court} holds the land now. ${p.arrive === '' ? 'Soon' : `By ${p.arrive}`} it is ours, unless you stop us.`,
};

const careful: LetterKindTexts = {
  JOINED: (p) => `Thank you for your trust. I gladly join ${p.guild}; may we keep each other safe.`,
  REFUSED_TRUST: () =>
    'Forgive me, I am a cautious soul. I hardly know you yet. Help me in a hard hour, and I will gladly say yes.',
  REFUSED_FULL: (p) => `I would like to, truly, but ${p.guild} is full. Write again when there is room.`,
  LEFT_TRUST: (p) => `It grieves me, but I can no longer rely on you. I leave ${p.guild}. Fare well.`,
  LEFT_ATTACKED: (p) => `You attacked me, your own ally. I leave ${p.guild} at once. How could you?`,
  ORDER_ATTACK: (p) =>
    `I go, though my heart is heavy. ${p.troops || 'My people'} march on ${p.target}${opt(p.arrive, ` and land at ${p.arrive}`)}.`,
  ORDER_ATTACK_PLANNED: (p) =>
    `Very well, we strike together at ${p.target}. I leave${opt(p.depart, ` at ${p.depart}`)}${opt(p.troops, ` with ${p.troops}`)}${opt(p.arrive, ` and land at ${p.arrive}`)}, right after you.`,
  ORDER_SUPPORT: (p) =>
    `Of course I help. ${p.troops || 'My people'} are on their way to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}. Keep them as long as you need.`,
  ORDER_SEND: (p) =>
    `Gladly. ${p.goods || 'My merchants'} are on their way to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}.`,
  ORDER_HOLD: (p) => `I will leave ${p.target} in peace, as you ask.`,
  ORDER_REFUSED_TRUST: (p) =>
    `Forgive me, but I do not know you well enough yet to send my people to ${p.target} on your word.`,
  ORDER_REFUSED_BOUND: (p) => `I cannot. ${p.target} belongs to friends of mine, and I gave them my word.`,
  ORDER_TOO_STRONG: (p) => `I am afraid ${p.target} is too strong for me. I would only lose good people there.`,
  ORDER_NO_TROOPS: (p) => `I am sorry, I need every defender I have. None can go to ${p.target} now.`,
  ORDER_NO_SURPLUS: (p) => `I am sorry, I have nothing to spare for ${p.target}. Every stone goes into my own walls.`,
  ORDER_NO_ROOM: (p) => `Your storehouses in ${p.target} are already full. I would only waste it.`,
  ORDER_NOT_IN_TIME: (p) => `I cannot reach ${p.target} in time to land with you. Forgive me.`,
  ORDER_UNSEEN: (p) => `I cannot see what waits in ${p.target}. I dare not send my people blind.`,
  ORDER_CAPS: (p) => `I have pressed ${p.target} enough for one day. Ask me again tomorrow.`,
  ORDER_PROTECTED: (p) => `${p.target} is still under protection. Nobody may attack it yet.`,
  HELP_SUPPORT: (p) =>
    `Please, I need you. ${army(p)} marches on ${p.target}${opt(p.arrive, ` and lands at ${p.arrive}`)}. I cannot hold alone: send ${defenders(p)}, I beg you.`,
  HELP_RESOURCES: (p) =>
    `${p.goal === 'WALL' ? 'My wall' : 'My next noble'} at ${p.target} is stuck, and I worry. Could you spare ${p.goods || 'some resources'}${opt(p.arrive, ` before ${p.arrive}`)}?`,
  THANKS: (p) =>
    p.goods === ''
      ? 'You came when I needed you most. Thank you, with all my heart.'
      : `You came when I needed you most. Thank you, with all my heart. ${p.goods} are on their way to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}.`,
  WARNING: (p) =>
    `I have not heard from you in a long while, and it worries me. I fear ${p.guild} means less to you than it did.`,
  COURT_TAUNT: (p) =>
    `I am sorry it comes to this. ${p.court} has the land in hand, and ${p.arrive === '' ? 'soon' : `by ${p.arrive}`} it will be ours.`,
};

const bold: LetterKindTexts = {
  JOINED: (p) => `Done. My banner flies with ${p.guild}. Point me at your enemies.`,
  REFUSED_TRUST: () => 'No. Show me what you are worth on the field first.',
  REFUSED_FULL: (p) => `${p.guild} is full. Throw someone out, then ask.`,
  LEFT_TRUST: (p) => `You are not worth my banner. I leave ${p.guild}.`,
  LEFT_ATTACKED: (p) => `You dared to attack me. I leave ${p.guild}, and next time I come with axes.`,
  ORDER_ATTACK: (p) => `Gladly. ${p.troops || 'My men'} on ${p.target}${opt(p.arrive, `, landing at ${p.arrive}`)}.`,
  ORDER_ATTACK_PLANNED: (p) =>
    `We hit ${p.target} together. I leave${opt(p.depart, ` at ${p.depart}`)}${opt(p.troops, ` with ${p.troops}`)}${opt(p.arrive, `, landing at ${p.arrive}`)}. Don’t be late.`,
  ORDER_SUPPORT: (p) =>
    `${p.troops || 'My men'} to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}. Don’t waste them.`,
  ORDER_SEND: (p) => `${p.goods || 'Goods'} to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}. Use it well.`,
  ORDER_HOLD: (p) => `Fine. ${p.target} lives, for now.`,
  ORDER_REFUSED_TRUST: (p) => `No. I don’t march on ${p.target} for someone I don’t trust.`,
  ORDER_REFUSED_BOUND: (p) => `No. ${p.target} is one of my own.`,
  ORDER_TOO_STRONG: (p) => `${p.target} would beat me today. I don’t lose battles on purpose.`,
  ORDER_NO_TROOPS: (p) => `My men are busy. None for ${p.target}.`,
  ORDER_NO_SURPLUS: (p) => `Nothing to spare for ${p.target}. I build first.`,
  ORDER_NO_ROOM: (p) => `${p.target} is full to the roof. Spend it first.`,
  ORDER_NOT_IN_TIME: (p) => `Too far. I can’t land at ${p.target} with you.`,
  ORDER_UNSEEN: (p) => `I don’t know what’s in ${p.target}. I don’t march blind.`,
  ORDER_CAPS: (p) => `${p.target} has had enough of me today. Tomorrow.`,
  ORDER_PROTECTED: (p) => `${p.target} is protected. Not yet.`,
  HELP_SUPPORT: (p) =>
    `${p.attacker || 'Someone'} thinks ${p.target} is easy prey. Prove them wrong: ${defenders(p)}${opt(p.arrive, ` before ${p.arrive}`)}.`,
  HELP_RESOURCES: (p) =>
    `${p.goal === 'WALL' ? 'My wall' : 'My next noble'} at ${p.target} is stuck. I need ${p.goods || 'resources'}${opt(p.arrive, ` before ${p.arrive}`)}.`,
  THANKS: (p) =>
    p.goods === ''
      ? 'You came. Good. I won’t forget it.'
      : `You came. Good. ${p.goods} are on the way to ${p.target}${opt(p.arrive, `, there at ${p.arrive}`)}.`,
  WARNING: (p) => `You have gone quiet. An ally who does nothing is no ally. Remember ${p.guild}.`,
  COURT_TAUNT: (p) =>
    `${p.court} takes the land. ${p.arrive === '' ? 'Soon' : `By ${p.arrive}`} it is ours. Try and stop us.`,
};

export const enLetters: Record<NonNullable<LordLetter['voice']>, LetterKindTexts> = {
  STEADY: steady,
  CAREFUL: careful,
  BOLD: bold,
};
