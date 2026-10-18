package de.bannerfall.common;

/**
 * Machine-readable error codes. Sent as {@code code} in every error response; the frontend maps
 * them to translated messages (web/src/i18n, section {@code errors}). Add codes in the milestone
 * that needs them (list in docs/plan/04-api.md).
 */
public enum ErrorCode {
    // General
    VALIDATION,
    UNAUTHENTICATED,
    FORBIDDEN,
    CSRF_TOKEN_INVALID,
    INTERNAL_ERROR,
    /**
     * Unknown path (Spring's own 404). Game objects have their own codes (VILLAGE_NOT_FOUND ...).
     */
    NOT_FOUND,
    /** Any other 4xx Spring answers by itself: wrong HTTP method, unsupported media type ... */
    BAD_REQUEST,

    // Accounts (M1)
    INVALID_INVITE_CODE,
    USERNAME_TAKEN,
    BAD_CREDENTIALS,
    TOO_MANY_LOGIN_ATTEMPTS,
    ACCOUNT_NOT_FOUND,

    // World and villages (M3)
    WORLD_FULL,
    VILLAGE_NOT_FOUND,

    // Buildings (M4)
    NOT_ENOUGH_RESOURCES,
    NOT_ENOUGH_POPULATION,
    REQUIREMENTS_NOT_MET,
    MAX_LEVEL_REACHED,
    QUEUE_FULL,
    BUILD_ORDER_NOT_FOUND,
    /** Moving a build order that has started, or to a place in front of one (M4c). */
    ORDER_NOT_MOVABLE,

    // Recruitment (M5)
    /**
     * Cancelling a recruit order the village doesn't have (any more), e.g. all its units are done.
     */
    RECRUIT_ORDER_NOT_FOUND,
    /** Deleting a recruit template the player doesn't have (any more) (404, 1.4). */
    RECRUIT_TEMPLATE_NOT_FOUND,

    // Dismissing units
    /**
     * Cancelling a dismiss order the village doesn't have (any more), e.g. all its units are gone.
     */
    DISMISS_ORDER_NOT_FOUND,
    /**
     * Cancelling a dismiss order while an attack is on its way to the village: the units of an
     * order can't be hit, so a cancel then would be a way to hide troops (decided 2026-10-11).
     */
    DISMISS_LOCKED,

    // Movements (M7)
    /** No village at the target coordinates (404). */
    TARGET_NOT_FOUND,
    /** Cancelling a movement the player doesn't have (any more), e.g. it has arrived (404). */
    MOVEMENT_NOT_FOUND,
    /** Withdraw / send back of a support stack that doesn't exist or isn't the player's (404). */
    STACK_NOT_FOUND,
    /** More units than there are at home (or in the stack). */
    NOT_ENOUGH_UNITS,
    /** A command or a withdraw without a single unit. */
    EMPTY_COMMAND,
    /** Attacking one of your own villages. */
    OWN_VILLAGE,
    /** A command to the village it starts from. */
    SAME_VILLAGE,
    /** The target player's beginner protection lasts beyond the arrival. */
    PROTECTED_TARGET,
    /** Nobles farther than {@code nobleMaxDistance}. */
    NOBLE_TOO_FAR,
    /** Nobles in a support. */
    NOBLE_ONLY_IN_ATTACK,
    /** Cancelling after the cancel window, after (or at) the arrival, or a RETURN. */
    CANCEL_WINDOW_OVER,

    // Reports (M8)
    /** A report the player doesn't have (never had, someone else's, or deleted) (404). */
    REPORT_NOT_FOUND,
    /** Sending a report whose stored payload can't be read any more (409, M40). */
    REPORT_UNREADABLE,

    // Chat (M9)
    /** More than one chat message per second. */
    CHAT_RATE_LIMIT,

    // Barbarians (M10)
    /** Dev cheat: the field for a barbarian village is off the map or already has a village. */
    FIELD_NOT_FREE,

    // Conquest (M11)
    /** Respawning while the player still has a village (or is an NPC lord) (409). */
    RESPAWN_NOT_ALLOWED,

    // Market (M13)
    /** More merchants than the village has free (busy on the way or reserved by offers). */
    NOT_ENOUGH_MERCHANTS,
    /** An offer whose want/offer ratio is outside {@code 1/tradeMaxRatio … tradeMaxRatio}. */
    INVALID_RATIO,
    /** An offer that gives and wants the same resource. */
    SAME_RESOURCE,
    /** A transport without resources. */
    EMPTY_TRANSPORT,
    /** Accepting an offer of one of your own villages. */
    OWN_OFFER,
    /** Accepting an offer more times than it has left. */
    OFFER_EXHAUSTED,
    /** An offer that doesn't exist (any more), or a foreign one to cancel (404). */
    OFFER_NOT_FOUND,
    /** Trading with the market is switched off in this world ({@code marketExchangeRatio} 0). */
    EXCHANGE_OFF,
    /** More than the village may still get from the market today (also: no market). */
    EXCHANGE_LIMIT,
    /** What the market would give doesn't fit into the warehouse. */
    WAREHOUSE_FULL,

    // Ranking and admin (M14a)
    /** A player that doesn't exist or was deleted by the admin (404). */
    PLAYER_NOT_FOUND,
    /** An admin tried to delete his own player (409). */
    CANNOT_DELETE_SELF,
    /** A chat message that doesn't exist (404). */
    MESSAGE_NOT_FOUND,
    /** An event that doesn't exist (404). */
    EVENT_NOT_FOUND,
    /** Retrying an event that isn't FAILED (409). */
    EVENT_NOT_FAILED,

    // Host console (M14d)
    /** Removing the admin role from the last admin (409). */
    LAST_ADMIN,
    /** Too many wrong host tokens within a minute (429). */
    TOO_MANY_ADMIN_ATTEMPTS,
    /** Changing your own password with a wrong current password (409). */
    WRONG_PASSWORD,

    // Abuse protection (M14e)
    /** The account is banned (403): login, or a session of an account banned meanwhile. */
    ACCOUNT_BANNED,
    /** The write rate limit of the account (429). */
    TOO_MANY_REQUESTS,
    /** Banning an admin: take the role away first (409). */
    CANNOT_BAN_ADMIN,
    /** A request body larger than the API accepts (413). */
    BODY_TOO_LARGE,

    // Pre-game (M14g)
    /** A game request while the world has not opened yet (409): the pre-game has no map. */
    WORLD_NOT_OPEN,
    /** Pre-game only: map size, setting or moving the opening time once the world is open (409). */
    WORLD_ALREADY_OPEN,

    // World end (v1.3, 02-game-rules §22)
    /**
     * A request that would change the game after the world has ended (409): the world is frozen,
     * only reading, the chat and the account stay open.
     */
    WORLD_ENDED,
    /** Taking an end back while the world has not ended (409). */
    WORLD_NOT_ENDED,

    // Start-up (code review 1.2, D3)
    /**
     * A game request right after a restart, while the event worker still catches up with what came
     * due during the downtime (503 with Retry-After).
     */
    SERVER_STARTING,

    // Guilds (M15a)
    /** Creating a guild, inviting or accepting while the host has guilds switched off (409). */
    GUILDS_DISABLED,
    /** A guild that doesn't exist (404). */
    GUILD_NOT_FOUND,
    /** Creating, accepting or inviting somebody who already is in a guild (409). */
    ALREADY_IN_GUILD,
    /** Leaving, inviting ... without being in a guild (409). */
    NOT_IN_GUILD,
    /** A leader's action by a plain member (403). */
    NOT_GUILD_LEADER,
    /** The name is taken by another guild, ignoring case (409). */
    GUILD_NAME_TAKEN,
    /** The tag is taken by another guild, ignoring case (409). */
    GUILD_TAG_TAKEN,
    /** The guild has {@code guildMaxMembers} members (409). */
    GUILD_FULL,
    /** The leader leaves a guild with other members: hand the lead over first (409). */
    LEADER_MUST_TRANSFER,
    /** An invitation that doesn't exist (any more) or isn't the player's / the guild's (404). */
    INVITATION_NOT_FOUND,
    /** Inviting somebody the guild has invited already (409). */
    ALREADY_INVITED,
    /** Kicking or handing the lead to somebody who is not a member of the leader's guild (404). */
    GUILD_MEMBER_NOT_FOUND,
    /** The invited player got another invitation less than a minute ago (429). */
    INVITATION_RATE_LIMIT,

    // Private messages (M17)
    /** Messaging or blocking yourself (409). */
    OWN_PLAYER,
    /** More than one private message per second (429). */
    MESSAGE_RATE_LIMIT,
    /**
     * The recipient already has 30 unread messages from this sender (409); since M40 also 30 unread
     * reports the sender sent him.
     */
    INBOX_FULL,
    /** Messaging a player the sender has blocked: unblock him first (409). */
    PLAYER_BLOCKED,
    /** Blocking when the block list has 100 players (409). */
    BLOCK_LIST_FULL,

    // Achievements (M16)
    /** The host named an achievement that doesn't exist (404). */
    ACHIEVEMENT_NOT_FOUND,

    // Farm helper (M18)
    /** A farm list entry the village doesn't have (any more) (404). */
    FARM_TARGET_NOT_FOUND,
    /** The target is on the village's farm list already (409). */
    ALREADY_IN_FARM_LIST,
    /** The village's farm list has 50 targets (409). */
    FARM_LIST_FULL,
    /** The player has no farm template with that letter (404, 1.4). */
    FARM_TEMPLATE_NOT_FOUND,

    // Unit levels (M30)
    /** Researching a unit level while the host has unit levels switched off (409). */
    UNIT_LEVELS_DISABLED,
    /**
     * The village's smithy already researches something, or this unit is being researched in
     * another village of the player (409). A unit without levels, or at its top level, answers
     * {@code MAX_LEVEL_REACHED}.
     */
    RESEARCH_BUSY,
    /** Cancelling a research the village doesn't have (any more), e.g. it is done already (404). */
    RESEARCH_ORDER_NOT_FOUND,

    // Paladin (M31)
    /**
     * Recruiting a paladin while every slot of the player is taken by a paladin he has, is training
     * or is waiting to be dismissed (409).
     */
    PALADIN_SLOTS_FULL,
    /** Recruiting a paladin in a world whose host has set the maximum slots to 0 (409). */
    PALADIN_DISABLED,

    // Banners (M32)
    /** Equipping a banner while the host has banners switched off (409). */
    BANNERS_DISABLED,
    /** A banner the player doesn't have (any more), or that belongs to somebody else (404). */
    BANNER_NOT_FOUND,
    /** Equipping a banner that already flies in a village: unequip it first (409). */
    BANNER_NOT_IN_STASH,
    /** Equipping a banner in a village without a Banner Hall (409). */
    BANNER_HALL_REQUIRED,
    /**
     * Equipping, swapping or unequipping while an attack is on its way to the village: a defender
     * can't hide the banner in the stash or pull a better one out at the last minute (409). A
     * paladin standard is also locked while an attack with a paladin from the village is under way
     * (1.4).
     */
    BANNER_LOCKED,
    /** Unequipping from a village that flies no banner (409). */
    NO_BANNER_EQUIPPED,
    /** Fusing banners of different tiers: the three must have the same tier (409, M32c). */
    BANNER_TIERS_DIFFER,
    /** Fusing banners of the highest tier: there is no tier above III (409, M32c). */
    BANNER_TIER_MAX,
    /** Fusing into a type none of the three banners has (409, M32c). */
    BANNER_FUSE_TYPE,
    /**
     * Fusing village banners with paladin standards: the three must be of one family (409, 1.4,
     * 02-game-rules §21.18).
     */
    BANNER_FAMILIES_DIFFER
}
