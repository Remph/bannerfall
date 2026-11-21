<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/readme-header-dark.png">
    <img src="docs/images/readme-header-light.png" alt="Bannerfall" width="480">
  </picture>
</h1>

<p align="center"><b>A browser strategy game: build villages, raise armies, win over the lords or take their land.</b><br>
Play it alone on your own PC, or host a world for your friends. Free software, in English and German, made for phones first.</p>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/images/readme-map-dark.png">
  <img src="docs/images/readme-map-light.png" alt="The map on a desktop by day: the player's villages, NPC lords and barbarian villages among hills, woods and a river, an ally's request for help above it and the overview map beside it">
</picture>

Bannerfall is a strategy game in the style of the classic "villages, troops, attacks" browser games. You start with one village, build it up, train troops, raid barbarians and fight computer-controlled NPC lords or your friends until one side holds the map.

## Play

### Alone, on your own PC

Single player needs no server and no domain: one command starts a world for you and the NPC lords on your own computer. The world sleeps while you are away and goes on where you left it.

You need [Docker](https://docs.docker.com/get-docker/) (Docker Desktop on Windows and macOS) and a copy of this repository. On Windows, run the command in WSL or Git Bash.

```
git clone https://github.com/Remph/bannerfall.git
cd bannerfall
ops/bannerfall-solo
```

The first start writes `.env.solo` with fresh secrets, builds the game (a few minutes the first time) and starts it. Then:

1. Open <http://localhost:8461> and register with the invite code `change-me`.
2. Open the setup link the command printed (`http://localhost:8461/setup?code=…`, valid for 30 minutes) in the same browser. It makes you the host and starts the guided setup.
3. Pick the starting point **Solo**: you alone against eight lords in lord guilds. It also replaces the invite code `change-me` with a fresh one (shown in the summary), so nobody else can register. Go through the steps (every value can still be changed), set the opening time and press *Finish setup*. At that moment you get your village and the game starts; *Open the world now* on the admin page (tab World) starts it at once.

The game only listens on your own computer; nobody else on your network reaches it. It also answers only under the names `localhost` and `127.0.0.1`, so a website you visit can't reach it by pointing its own name at your computer (DNS rebinding). While you play, the world clock runs; about two minutes after you close the game the world falls asleep, and nothing happens until you come back. As the host you can also pause it or let it run two or four times as fast (admin page, tab World, or `ops/bannerfall-solo admin world clock pause|play|fast2|fast4`).

| Command | What it does |
|---|---|
| `ops/bannerfall-solo` | Start the game (again) and print the setup link while the world still needs one |
| `ops/bannerfall-solo stop` | Stop it (the world sleeps from that moment until the game runs again) |
| `ops/bannerfall-solo update` | Save a backup, then rebuild the game from this checkout and restart it, for example after `git pull` or `git checkout v2.1.0` |
| `ops/bannerfall-solo backup` | Save the world to `backups/bannerfall-solo-<date>.sql.gz` |
| `ops/bannerfall-solo restore <file>` | Put a saved world back (asks first) |
| `ops/bannerfall-solo reset` | Delete the world and start afresh (asks first) |
| `ops/bannerfall-solo admin …` | The host console, for example `admin status` or `admin world clock` |

The world lives in the Docker volume `bannerfall-solo_pgdata`; `ops/bannerfall-solo reset` (or `docker compose -f compose.solo.yaml --env-file .env.solo down -v`) deletes it, and the next start begins a new world. To use a released image instead of building one, set `BANNERFALL_IMAGE` in `.env.solo` (the comment at the top of `compose.solo.yaml` shows how). A friend can still join a solo world; it then only sleeps while nobody plays.

### A world for friends

For a group, one of you runs the world on a small server with a domain: one Docker image with its database behind Caddy, which fetches the HTTPS certificate by itself. Everybody plays in the browser, on the phone or the computer. `docs/hosting.md` walks through it step by step on a fresh Linux server (DNS, SSH, firewall, Docker, first start, backups, updates); *Hosting a world* below is the short version. The guided setup offers the starting points *Friends' round*, *Long-running world*, *Together against the lords*, *Solo* and *Custom*.

<p align="center">
  <img src="docs/images/readme-phone-village.png" alt="A village on a phone by day: the drawn village with every building and its level, and an ally's request for resources under the sky" width="260">
  &nbsp;
  <img src="docs/images/readme-phone-diplomacy.png" alt="The diplomacy page on a phone: the Raven Court and its race for the land against your guild, and an allied lord with its trust" width="260">
  &nbsp;
  <img src="docs/images/readme-phone-chronicle.png" alt="The chronicle of an ended world on a phone: a lord joining as an ally, a help request answered in time and the villages that changed hands, day by day" width="260">
</p>

## What is in the game

**Villages**
- Sixteen buildings, shown as a drawn village or as a list, with a build queue; buildings can be demolished again, one level at a time.
- Wood, clay and iron, a warehouse, a hiding place and a farm that limits the population.
- A market for sending resources to your own and other villages, trade offers between players, and an exchange with the market itself.
- Village automation from the third village on: build plans that work through their buildings and levels by themselves, also while you are offline, and refill a recruit template.
- A private note and tags on any village.

**Troops and battle**
- Thirteen units: infantry, cavalry, scouts, rams, catapults, nobles, the paladin and the banner thief; archers and mounted archers are optional.
- Attacks, supports and scouting, attack trains (several attacks that land together, in order), walls, rams, catapults, loot and luck. Morale weakens attacks on much smaller players; a night bonus is optional.
- Conquest with nobles, who lower a village's loyalty; every village and noble beyond the first few makes the next noble dearer.
- Unit levels researched in the Smithy (optional), the paladin and the banner thief trained in the Banner Hall.
- Banners found in battle, flown from the Banner Hall, fused and stolen, and standards that only paladins carry.
- Battle and scout reports, an attack simulator, a farm list with troop templates and "send all", and recruit templates.

**The NPC lords and playing alone**
- NPC lords play the same game: they build, farm, attack, conquer, join lord guilds, ship their surplus and can learn from the players' attacks. Each comes in one of nine variants, from the Robber Knight and the Conqueror to the Castellan, the Merchant Lord and the Vulture; you learn which one a lord is once you have met it in battle.
- Every lord remembers what you do to it. Supports, shipments and beating its enemies raise its trust; attacks and scouting cost it.
- In a solo world you can invite a lord that trusts you enough into your guild. An ally fights on your side and wins with you, and it leaves again when its trust falls below zero.
- Give your allies orders from the map: attack a village (landing together with your own attack), support or supply one of yours, or leave a village alone. Each lord answers in its own voice, and says so when it cannot do it.
- Allies ask for help in turn: defenders against an attack they cannot hold, or resources for a noble or their wall. An answer in time earns trust and a thank-you shipment; ignoring them costs it.
- The strongest lord guild becomes the court, with a name of its own (the Raven Court, the Bear Court, …), and races you for the map. The diplomacy page shows the race, your allies and every lord's trust.
- A solo world sleeps while you are away: attacks, builds and the lords' plans wait and go on where they stopped. The host can pause it or play it two or four times as fast.

**The world**
- A drawn map with terrain, movement lines and markers, owner and guild colours, an overview map and farming straight from the map.
- Barbarian villages that grow and can build walls.
- Four map spreads (Close, Normal, Wide, Vast), and friends in one guild start next to each other.
- Victory by domination (holding most of the villages), by holding the banner keeps around the centre, or by the most points on an end date.
- An ended world freezes and stays readable, and tells its story in a chronicle: a map that replays who held which village day by day, and the turning points, from the first conquest to allies won and lost and the end.

**Together**
- Guilds with their own chat, a political map and enemy marks; a world chat, private messages and blocking.
- A ranking by points, attacks, defence and loot, statistics with daily charts, and achievements.
- Push notifications on the phone or computer while the game is closed: incoming attacks, a lost village, messages, requests from allies, returning troops, a full warehouse.

**Look and comfort**
- The page is the land your village stands on and the header is the sky above it: the sun by day, the moon and stars at night.
- Five colour themes (Meadow, Cornfield, Frost, Heath and the stitched tapestry look), each by day and by night, and Black for OLED screens; medieval or classic icons.
- A "Now" list of everything that is running: builds, training, research, marches and merchants, in one place.
- Built for phones and touch first, then widened for the desktop; a tutorial for new players; English and German.

**For the host**
- One Docker image with PostgreSQL behind Caddy, which fetches the HTTPS certificate by itself; `compose.solo.yaml` for one player on a PC.
- A guided setup for a new world: a starting point (*Friends' round*, *Long-running world*, *Together against the lords*, *Solo* or *Custom*), then the pace, map, opponents, rules, victory and opening time.
- An admin page and the `bannerfall-admin` command line: players, bans, world settings, the world clock, events, NPC lords, an audit log and a log of suspicious requests.
- Signed releases, daily backups with an optional encrypted copy elsewhere, a restore script and updates in one command; several worlds on one server; optional login through an OpenID Connect account server, with one account for several worlds.

<picture>
  <img src="docs/images/readme-chronicle.png" alt="The chronicle of an ended world on a desktop: the replay map of who held what beside the turning points, day by day">
</picture>

The rules and all numbers are in `docs/plan/02-game-rules.md`. To run a world for others, follow `docs/hosting.md`.

Not affiliated with any existing game. Own name, own numbers, own village picture and map; the medieval icons are credited below.

## Hosting a world

Bannerfall runs as three containers from `compose.prod.yaml`: `app` (Spring Boot with the web app inside), `db` (PostgreSQL 18, not reachable from outside) and `caddy` (HTTPS and reverse proxy, only with the profile `caddy`).

A complete step-by-step guide for a fresh Linux server (DNS, SSH key login, firewall, Docker, first start, backup drill, updates) is `docs/hosting.md`.

**Once, on the server** (a vServer with Docker and the compose plugin, 2 vCPU / 4 GB RAM is comfortable; the DNS A/AAAA records of the domain point to it and ports 80 and 443 are open):

```
git clone https://github.com/Remph/bannerfall.git /opt/bannerfall    # or copy compose.prod.yaml, Caddyfile, .env.example and ops/
cd /opt/bannerfall
cp .env.example .env     # then edit: POSTGRES_PASSWORD, BANNERFALL_ADMIN_TOKEN (both start empty and must be filled), BANNERFALL_DOMAIN, BANNERFALL_IMAGE
chmod 600 .env
```

`openssl rand -hex 24` makes a good password and a good token (the token needs at least 32 characters). Keep `.env` private; it is not in git. The host cheats stay off (`BANNERFALL_CHEATS_ENABLED` is not passed to the container).

**Build and ship** (on your PC, in the repo root; replace `2.1.0` by the version):

```
docker build -t ghcr.io/your-name/bannerfall:2.1.0 --build-arg VERSION=2.1.0 .
docker login ghcr.io          # once, with a GitHub token that may write packages
docker push ghcr.io/your-name/bannerfall:2.1.0
```

(The Dockerfile builds the web app, the server jar and a small runtime image; the build takes a few minutes the first time. `VERSION` is shown by `bannerfall-admin status`. Without a registry: `docker save ghcr.io/your-name/bannerfall:2.1.0 | ssh server docker load`, or build on the server itself: `docker build -t bannerfall:2.1.0 --build-arg VERSION=2.1.0 .` in the cloned repo and `BANNERFALL_IMAGE=bannerfall:2.1.0` in `.env` (needs about 4 GB of memory including swap). A private package needs `docker login ghcr.io` on the server too.)

**Start or update** (on the server): set `BANNERFALL_IMAGE=ghcr.io/your-name/bannerfall:2.1.0` in `.env`, then

```
docker compose -f compose.prod.yaml pull        # skip this when the image was loaded or built on the server itself
docker compose -f compose.prod.yaml up -d
docker compose -f compose.prod.yaml ps          # app and db "healthy"
```

The database migrates itself at start (Flyway). Everybody is logged out by an update (sessions are in memory); announce it with `bannerfall-admin broadcast "Update in 5 minutes"`. Caddy asks Let's Encrypt for the certificate as soon as it starts, so the DNS record must already point to the server (check with `nslookup`; repeated failures run into Let's Encrypt's rate limit). `docker compose -f compose.prod.yaml logs caddy` shows `certificate obtained successfully`; <https://your-domain> should answer within a minute. Use the world's own name as `BANNERFALL_DOMAIN` (for example `play.example.com`), not the main domain of a landing page you may run beside it.

**Updating in one command** (since 1.4.1, in a git checkout on the server): `ops/bannerfall-update 2.1.0` does the steps of an update in order and stops at the first one that fails. It checks that the checkout has no local changes, fetches the tags, pulls the image for exactly that version (the image `BANNERFALL_IMAGE` names now, with the new tag; `--repo` names another, `--build` builds `bannerfall:<version>` on the server instead), checks the tag out, checks that its `compose.prod.yaml` works with your `.env`, takes a backup with `ops/bannerfall-backup`, warns the players in the chat, waits a minute (`--wait <seconds>`, `0` for no warning), sets `BANNERFALL_IMAGE` in `.env`, restarts the app, waits until it is healthy and reports the new version, and runs `bannerfall-admin check`. It shows the plan and asks once first (`--yes` skips the question). Nothing changes for the players before the backup is done, and stopping it before the restart (Ctrl+C, or a dropped connection) puts the old checkout back. If the new version doesn't come up and ran no database migration, the script puts the old image and checkout back by itself. If it already migrated the database, it stops and prints how to restore the backup it took. Read the release notes first, since some releases add settings or need a step of their own. `ops/bannerfall-update --help` lists the options.

**Signed releases** (since 2.0). Every release tag is signed with the release key, and its message names the server image by digest (`Image: ghcr.io/…/bannerfall@sha256:…`; the digest is in the release notes too). `ops/bannerfall-update` runs `ops/bannerfall-verify-tag` first: the tag must be signed by a key listed in `ops/release-signers` **of the version that runs now**, so a stolen GitHub login or deploy key can publish a tag but not one that passes. Nothing of an unverified release is checked out, pulled or run, and the image is pulled by the digest of the verified tag (`BANNERFALL_IMAGE=…:2.0.1@sha256:…` in `.env`), not by a tag the registry could be made to move. It needs `git` and `ssh-keygen` (`apt install openssh-client`). The key is `ops/release-signers` (a release may replace it, and the next update checks against the new one); for a pin outside the checkout set `BANNERFALL_RELEASE_SIGNERS=/etc/bannerfall/release-signers`, and `BANNERFALL_REQUIRE_SIGNED=1` turns "no key pinned" from a warning into a failure. The first update to a release that has the check is made by the old script, which does not check; do it yourself once: `git fetch --tags origin && git verify-tag` with the key from the release notes, see `docs/hosting.md`, section 10. `--build` skips the digest (the image is built from the checked tag).

**Patches by themselves** (optional): `ops/bannerfall-auto-update` in a nightly cron line installs the newest patch of the minor version the world runs (2.1.0 to 2.1.1, never to 2.2.0) with `ops/bannerfall-update`: backup, a five-minute warning in the chat, health check, and the automatic rollback described above. It then moves the shared Caddy checkout (`edge/`) to the same tag. A new minor version is only mentioned in its log; update to it by hand. A file `auto-update.hold` pauses it, a version that failed is not tried again, and an optional free healthchecks.io address tells you about failures and about runs that didn't happen. `docs/hosting.md`, section 10, has the cron line and the details.

**Feature switches.** Some of the bigger features can be switched per world. A new world starts with banners, the paladin, morale and the rising noble price on, and with barbarian walls, unit levels, archers, learning NPC lords and the solo mode off. The guided setup asks for the important ones. What each release switches in a running world is in its release notes (`docs/releases/`, *Heads-up for running worlds*).

| Feature | Setting (`bannerfall-admin world set …`, or the admin page, tab World) | Default |
|---|---|---|
| Barbarian walls (02 §11) | `barbarian-walls-enabled true`; also `barbarian-wall-start-days`, `barbarian-wall-max-level`, `barbarian-walls-rebuild` | off |
| Unit levels in the Smithy (02 §5.4) | `unit-levels-enabled true` | off |
| Paladin (02 §5.5; his numbers are placeholders until there is more play data) | `paladin-max-slots 0` turns him off; `paladin-villages-per-slot` | on (4 villages per slot, at most 5) |
| Banners (02 §21) | `banners-enabled false` turns them off (existing banners are kept, asleep); `banner-drop-factor` (0–3, 0 = no drops) | on |
| Archers (02 §5.6) | `archers-enabled true` | off |
| Morale (02 §8.2) | `morale-enabled false` turns it off; `morale-floor` (30–100) | on (floor 30 %) |
| Rising noble price (02 §5.7) | `noble-price-step` (0–1, 0 = the same price for every noble) | 0.25 |
| Village automation (02 §25) | `automation-villages` (from a player's N-th village; 0 = none) | from the 3rd village |
| NPC lords learning from players (02 §11b) | `npc-learning-enabled true` | off |
| Friends start next to their guild (02 §2) | `guild-start-together false` turns it off | on |
| Solo world: sleeps while nobody plays, lords as allies (02 §28 to §31) | `solo-mode true` (the *Solo* starting point sets it) | off |

**First steps in a new world** (order matters: do 1 and 2 before telling anybody the address). A fresh production world starts in the **pre-game**: friends can register and chat, nobody has a village yet, and the map is created at the **opening time** you set in the setup (or on the admin page, tab World). Until then you can still change everything, including the map spread (how far apart players start: Close, Normal, Wide or Vast), map size, barbarians and NPC lords.

1. Register yourself in the browser, then ask for a one-time setup link: `docker compose -f compose.prod.yaml exec app bannerfall-admin setup`. It prints `https://<your domain>/setup?code=…` (the domain comes from `BANNERFALL_DOMAIN` in `.env`), valid for 30 minutes and only once.
2. Open the link while logged in: it makes your account the admin and starts the **guided setup**, a wizard with a starting point (*Friends' round*, *Long-running world*, *Together against the lords*, *Solo* or *Custom*) and one step each for the basics (name, invite code, guilds), the pace, the map and opponents, the rules, the victory and the opening time. Every step is saved when you go on; you can skip it and use the admin page instead. Without the wizard: `... bannerfall-admin world set invite-code <secret>` (the default is `change-me`), `... bannerfall-admin player YourName role admin --yes` and the admin page.
3. Tell your friends the address and the invite code. They can register right away.
4. Change the opening time if you like (setup, admin page, or `... bannerfall-admin world open --at "2026-10-17 18:00"`; `--clear` takes the time back, `--now` opens at once). At that moment every registered player gets a village and the game starts. The login page shows the opening time and a countdown.

How the world is won is set on the admin page (tab World, cards *Victory* and *End of the world*) or with `world set victory-mode`: by default a side (a guild, or a player without one) wins once it holds 60 % of all villages for 48 hours; `KEEPS` places banner keeps around the map centre instead, and `NONE` leaves the end to you. An end date (`world end --at …`) works in every mode: the most points win then. An ended world is frozen and stays readable; `world reopen` takes the end back. The rules are in `docs/plan/02-game-rules.md` §22.

Everything else about running the world is under *Operating the server* below. A short daily look keeps a world healthy: `bannerfall-admin status`, `events failed`, `check`, `suspicious` and `logs --lines 50`.

**Logs.** The app writes `server.log` and `server.err` into the volume `logs` (rolled daily and at 10 MB, 14 days): `docker compose -f compose.prod.yaml exec app tail -n 100 /app/logs/server.err`, or `bannerfall-admin logs`. The console output is in `docker compose -f compose.prod.yaml logs app`. Docker keeps at most three files of 10 MB of console output per container (`compose.prod.yaml`, `x-logging`), and a new container (every update) starts empty. Caddy keeps no access log, and its own log leaves the visitors' addresses out (`Caddyfile`); the privacy page (`/privacy`) tells players so.

**TrueNAS or another proxy instead of Caddy.** Leave `COMPOSE_PROFILES` empty in `.env` (no Caddy) and let your proxy forward the domain to the app's port (`APP_PORT`, default 8461; set `APP_BIND=0.0.0.0` in `.env` if the proxy is another machine or container, and keep the port closed to the internet). In nginx proxy manager turn on *Websockets Support*, set the scheme to `http`. The game keeps a WebSocket open; server and page send STOMP heartbeats every 10 seconds, so the proxy's default read timeout of 60 seconds never fires on a healthy connection (nothing to add under *Advanced*). The proxy must pass `X-Forwarded-For` and `X-Forwarded-Proto` (nginx proxy manager does) and keep the `Host` header: the app trusts them for the client address, the secure cookie and the WebSocket origin check. Hide `/actuator` from the internet in the proxy (it only holds the health check). The privacy page (`/privacy`) tells players that no access log is kept: switch your proxy's access log off, or tell your players about it yourself.

**Several worlds on one server.** Each world runs `compose.prod.yaml` in a folder of its own, with its own `COMPOSE_PROJECT_NAME` and `APP_PORT` in `.env` and no `caddy` profile; one shared Caddy from `edge/` holds ports 80 and 443 and passes each world's domain to that world's port. The step-by-step guide is `docs/hosting.md`, section 12.

**Ports.** Every port on the host is a setting in `.env` (see `.env.example`): `APP_PORT` (the app, default **8461**, only on `127.0.0.1` unless `APP_BIND` says otherwise), `HTTP_PORT` and `HTTPS_PORT` (Caddy, default 80 and 443). Change the Caddy ports only if a router forwards 80/443 to them: Let's Encrypt has to reach Caddy on 80/443 from the internet. Inside the containers nothing changes (the app listens on 8080, the database on 5432, neither reachable from outside). For development `compose.yaml` takes `DB_PORT` (default 5432) for the database on your PC, and the server reads the same variable.

### Operating the server

The host runs the container and has two doors, both calling `/api/admin/**`: the `/admin` page (log in as an ADMIN account, tabs Status, Players, World, Events, NPC lords, Suspicious, Cheats, Audit) and the command line `bannerfall-admin`.

**Token.** The CLI needs `BANNERFALL_ADMIN_TOKEN` (at least 32 characters, random; the server refuses to start with a shorter one; empty = no CLI). Make one with `openssl rand -hex 24` and put it in the server's environment. Five wrong tokens in a minute from one address block token requests from that address for a minute (429); the CLI on `localhost` inside the container isn't affected by guessing from outside.

**CLI.** In the container: `docker compose exec app bannerfall-admin` (menu) or with a command; locally `ops/bannerfall-admin` with `BANNERFALL_JAR=server/build/libs/bannerfall.jar` and the same token in the environment (`BANNERFALL_URL` if the server isn't on `http://localhost:8080`).

```
bannerfall-admin status
bannerfall-admin players
bannerfall-admin player Anna reset-password          # prints a new password once
bannerfall-admin player Anna reset-password --password   # asks for one (not shown, not in the history)
bannerfall-admin player Anna role admin --yes
bannerfall-admin player Anna revoke-achievement FIRST_BLOOD --reason "…"   # for good, they can't earn it again
bannerfall-admin world                  # all world settings
bannerfall-admin world set invite-code newcode
bannerfall-admin world set victory-mode KEEPS   # DOMINATION (default), KEEPS or NONE; see 02-game-rules §22
bannerfall-admin world end --at "2026-11-07 20:00"   # end date (Berlin time); --now, --clear; world reopen takes an end back
bannerfall-admin world set solo-mode true   # a world that sleeps while nobody plays, with lords as allies
bannerfall-admin world clock pause     # solo worlds: pause, play, normal, fast2, fast4; without an argument it shows the clock
bannerfall-admin events failed          # and: events retry <id>
bannerfall-admin npc list               # npc spawn <personality>, npc difficulty NORMAL
bannerfall-admin barbarians spawn 40 52
bannerfall-admin banner list Anna       # banner grant Anna WAR 1 --reason "…", banner revoke <id>
bannerfall-admin broadcast "Restart in 5 minutes"   # a popup for everybody who is playing; chat-say "…" posts in the chat instead
bannerfall-admin audit --limit 20
bannerfall-admin suspicious             # who sent requests the game never sends (last 30 days)
bannerfall-admin player Anna ban --reason "cheating" --days 7   # or --until <instant>; without either: permanent
bannerfall-admin player Anna unban
bannerfall-admin check                  # impossible states in the database (read only)
bannerfall-admin logs --lines 50
bannerfall-admin status --json          # --json on every command
```

Exit codes: 0 ok, 1 the server refused (or can't be reached), 2 wrong usage. Destructive commands ask for the name (or take `--yes`; without a terminal they need it).

**Cheats.** Off by default. `BANNERFALL_CHEATS_ENABLED=true` turns on `bannerfall-admin cheats …` and the Cheats tab (resources, units, finish orders and movements on any village); without it these answer 404. Status and the CLI show "CHEATS ON" while it is set. Every host action is in the audit log (`audit`, Audit tab).

**Abuse protection.** Everyone logged in can send requests by hand, so the server checks every rule itself. Writes are limited per account (10 per second, bursts of 30; `bannerfall.abuse.write-limit.*` in `application.yml`; beyond it `429`). Requests the web client never sends (another player's ids, impossible numbers, wrong CSRF token) are recorded in the suspicion log (`suspicious`, Suspicious tab, kept 30 days); 20 of them within 10 minutes lock the account for an hour (`bannerfall.abuse.auto-lock.*`, audited as `AUTO_LOCK`, shown in `status`; admins are exempt). Logins have a limit per address (5 wrong passwords per name and address in a minute, 10 logins per address and minute) and, since 2.0, one per account over all addresses: 20 wrong passwords within 30 minutes lock that name's logins until the half hour is over, so guesses spread over many addresses add up too. Players who are logged in are not affected; the lock ends when the half hour is over, or when the host sets a new password (`reset-password`). The same budget covers the password checks on the change-password page. Reading requests (`GET`) are not limited and nothing caps the WebSocket connections per player: on a server open to strangers, put a rate limit on your proxy. A ban (`ban` / `unban`, Players tab) refuses the login with the reason and the date and ends the account's open sessions and WebSockets at once; its villages stay. `check` (and a nightly job, WARN lines in `server.log`) looks for impossible rows such as negative resources or movements without an event.

**Forgotten password.** `bannerfall-admin player <name> reset-password` (or Players tab) and tell the player; they can change it under Menu > Change password.

### Backups

```
ops/bannerfall-backup       # writes backups/bannerfall-<date>-<time>.sql.gz, keeps 14 days (KEEP_DAYS), checks the dump
```

Run it daily with cron on the server (`crontab -e`; the path is where you cloned the repo; the script must be executable, it is in git). Create the folder first (`mkdir -p /opt/bannerfall/backups`): the shell opens the log file before the script runs, so a missing `backups/` stops the very first run:

```
17 3 * * * /opt/bannerfall/ops/bannerfall-backup >> /opt/bannerfall/backups/backup.log 2>&1
```

A backup on the same disk is no protection against losing the server: copy `backups/` somewhere else now and then (`scp`, `rsync`, a cloud drive), or let the script do it every night (below). The volume `logs` and the Caddy certificates are not part of it (certificates are fetched again).

**An encrypted copy in a second place** (optional, since 2.0). A dump holds every account, so it is encrypted before it leaves the server: install `age` (`apt install age`), make a key pair **on your own computer** (`age-keygen -o bannerfall-backup-key.txt` prints the public key `age1…`; keep that file somewhere safe and off the server, without it the copies cannot be opened), and give the server only the public key. Then `BACKUP_AGE_RECIPIENT=age1…` and `BACKUP_OFFSITE_CMD='…'` in front of the cron command make every nightly backup also encrypt the dump and run your command with the encrypted file as `"$1"`, for example to another server (`rsync -e "ssh -i /root/.ssh/backup" "$1" backup@other-host:bannerfall/`), to a storage service with rclone (`rclone copyto "$1" remote:bannerfall/"$(basename "$1")"`) or to a mounted drive (`cp "$1" /mnt/usb/`). A dump is never sent unencrypted (the command without the key is refused), and an off-site copy that fails makes the run fail and report `/fail` while the local dump stays. The script does not delete old copies from the second place (give that place its own rule, for instance a lifecycle rule or `find … -mtime`). To restore from such a copy: `age -d -i bannerfall-backup-key.txt bannerfall-2026-11-10-0317.sql.gz.age > bannerfall-2026-11-10-0317.sql.gz`, then `ops/bannerfall-restore` as below. `ops/bannerfall-update` and `ops/bannerfall-restore` make no off-site copy. Tests: `ops/tests/backup-test.sh`.

A backup that stops silently is only noticed when it is needed. Set `BACKUP_PING_URL` in front of the cron command to the address of a check service (a "dead man's switch"; the free plan of healthchecks.io is enough, nothing here needs a paid one): a good backup requests that address, a failed one requests it with `/fail` added, and the service mails you when nothing arrives in time. The address is a plain `https://…` address without a query string. The cron line then reads `17 3 * * * BACKUP_PING_URL=https://hc-ping.com/<your-check-id> /opt/bannerfall/ops/bannerfall-backup >> /opt/bannerfall/backups/backup.log 2>&1`. It needs `curl` on the server; without the variable nothing changes, and an unreachable service never makes a backup fail.

### Restoring a backup

```
ops/bannerfall-restore backups/bannerfall-2026-10-09-0317.sql.gz
```

It asks you to type `restore`, stops the app, dumps the current database to `backups/pre-restore/` (your way back if you picked the wrong file), **replaces the whole database** with the dump (everything after the backup is lost), and starts the app again. If loading fails, it says so and leaves the app stopped. `--yes` after the file name skips the question. After restoring, check `bannerfall-admin status` and `bannerfall-admin check`. Restoring was tested with a local copy of this setup (register a player, back up, register another, restore: the second one is gone).

A backup of the old server also moves the world to a new one: install the stack as above, copy the dump over, `ops/bannerfall-restore <dump>`.

## Development

To change the game: the server is a Spring Boot app, the client a React app, both in this repository.

### Repository layout

| Folder | What |
|---|---|
| `server/` | Spring Boot app (Java 25, Gradle Kotlin DSL, PostgreSQL) |
| `web/` | React + TypeScript app (Vite, pnpm) |
| `docs/` | Game rules (`plan/02-game-rules.md`, banner art in `plan/banners/`), the hosting guide (`hosting.md`), the release notes (`releases/`) and the README images and clip (`images/`) |
| `compose.yaml` | Local development database (PostgreSQL 18) |
| `compose.solo.yaml` | Single player on your own PC: app and database, no Caddy (`ops/bannerfall-solo`) |
| `Dockerfile`, `compose.prod.yaml`, `Caddyfile`, `.env.example` | Production: the image, app + database + Caddy, settings template |
| `edge/` | The shared Caddy for several worlds on one server (`docs/hosting.md`, section 12) |
| `ops/` | `bannerfall-solo` (single player), `bannerfall-admin` (host console), `bannerfall-backup`, `bannerfall-restore`, `bannerfall-update`, `bannerfall-auto-update`, `bannerfall-verify-tag` and `release-signers` (signed releases), `tests/` |
| `.run/` | Shared IntelliJ run configurations |

### Prerequisites

- JDK 25 (Eclipse Temurin), with `JAVA_HOME` pointing to it
- Docker Desktop (running; also needed for the Testcontainers tests)
- Node 24 LTS and pnpm

### Run locally

1. Start the backend (starts the database from `compose.yaml` automatically):

   ```
   cd server
   ./gradlew bootRun
   ```

   The API runs on <http://localhost:8080>, Swagger UI at <http://localhost:8080/swagger-ui.html>.
   To start the database yourself instead: `docker compose up -d` in the repo root.

2. Start the frontend:

   ```
   cd web
   pnpm install
   pnpm dev
   ```

   Open <http://localhost:5173>. Vite forwards `/api` and `/ws` to the backend.

3. To test on a phone in the same network: `pnpm dev --host`, then open the PC's LAN IP on port 5173.

### First admin

The `/admin` page and `/api/admin/**` need an account with the role `ADMIN`. There is no admin at the start: register normally in the app (the player and the starting village are created with it), then promote that account in the database and log in again (from then on a role change by the host works at once):

```
docker compose exec db psql -U bannerfall -c "update account set role = 'ADMIN' where username = 'YourName'"
```

(`compose.yaml`'s database service; with another database use your own `psql`. On a production server the host console does it: `docker compose -f compose.prod.yaml exec app bannerfall-admin player YourName role admin --yes`.) Never insert accounts by hand: every account needs its player and village, or `/api/me` fails. From then on the invite code, the world settings and failed events are on the `/admin` page. To try the game as a player without a development setup, use `ops/bannerfall-solo` (*Play* above).

### Tests and formatting

```
cd server
./gradlew check           # the tests (needs Docker running) and spotlessCheck
./gradlew test            # the tests only
./gradlew simulationTest  # the NPC lord simulation (30 days, the three scenarios side by side, prints balance tables; -PsimQuick for 15 days)
./gradlew spotlessApply   # format Java (google-java-format, AOSP style) and Gradle files
./gradlew spotlessCheck   # fails if something is not formatted (also part of ./gradlew check)

cd web
pnpm test
pnpm format               # Prettier
pnpm format:check
pnpm lint                 # ESLint
pnpm check:api            # the API types match src/api/openapi.json
pnpm build
```

There is no CI on pull requests in the development repository: run these before you open one.

After changing REST endpoints, update the web's copy of the API description and regenerate the typed
API client: `cd server && ./gradlew test --tests '*OpenApiSnapshotTest' -PupdateOpenApi`, then
`cd web && pnpm gen:api`. The checks fail while either is out of date (`OpenApiSnapshotTest`, `pnpm check:api`).

### Versions

- Java 25 (Temurin), Spring Boot 4.1, Gradle 9.8, PostgreSQL 18, springdoc-openapi 3.2, Spotless 8.10
- Node 24, pnpm, React 19, Vite 8, TypeScript 6 (exact versions: `server/build.gradle.kts`, `web/package.json`)

## Releases and issues

Releases are published to <https://github.com/Remph/bannerfall>, one commit per release with its tag (`v2.1.0`, …). Bug reports and ideas are welcome as issues there. Pull requests are not accepted, because the next release would overwrite them; describe the change in an issue instead.

## License

Bannerfall is free software under the [GNU Affero General Public License](LICENSE), version 3 or (at your option) any later version (`AGPL-3.0-or-later`). If you run a modified version of the server for other players, the AGPL requires you to offer them its source code.

The name "Bannerfall" and the bannerfall.de domain are not covered by the license. Third-party dependencies and the bundled web fonts (OFL) keep their own licenses.

The medieval icons in `web/src/components/medievalIcons.ts` are from [game-icons.net](https://game-icons.net), by Lorc, Delapouite, Skoll, HeavenlyDog, Cathelineau and DarkZaitzev, under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). They are drawn unchanged except for their colour and a slightly cropped frame; each line of that file names the original icon and its artist. The game credits them on its About page.
