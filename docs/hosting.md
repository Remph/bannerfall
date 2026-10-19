# Host your own Bannerfall realm

A step-by-step guide for running one Bannerfall world (a "realm") on a small Linux server of your own. It assumes Ubuntu 24.04 LTS on a rented virtual server, but any Linux host with Docker works the same way from step 4 on. The README has the reference for everything mentioned here (*Operating the server*, *Deploying*, *Backups*).

Plan about two hours the first time, most of it waiting for DNS and the first image build.

## 0. What you need

| Thing | Recommendation |
|---|---|
| Server | 2+ vCPU, 4 GB RAM (8 GB if you build the image on the server, which this guide does), 40 GB disk. The running stack needs about 1.4 GB (app 768m, database 512m, Caddy 128m). |
| Operating system | Ubuntu 24.04 LTS |
| A domain | One name for the realm, for example `play.example.com`, with an A (and, if your server has IPv6, AAAA) record pointing to the server |
| Open ports | 22 (SSH), 80 and 443 |
| On your PC | An SSH client (OpenSSH, or any GUI client that can use an Ed25519 key) |

The stack is three containers from `compose.prod.yaml`: `app` (the game: Spring Boot with the web app inside), `db` (PostgreSQL 18, never reachable from outside) and `caddy` (HTTPS with a Let's Encrypt certificate it fetches and renews by itself).

## 1. DNS first

Add the A record for your realm's name now (`play.example.com` → the server's IPv4), and an AAAA record only if the server has a working IPv6 address. DNS takes a while to spread; Caddy can only get its certificate once `nslookup play.example.com` returns the server's address.

## 2. First login and basic security

Order matters: **make a key, put it on the server, test it, and only then switch passwords off.** Find out first how your provider's browser console works (most have one): it is your way back in if SSH ever locks you out.

1. Make a key on your PC: `ssh-keygen -t ed25519 -C "me@pc"` (or your SSH client's key generator, type Ed25519). Keep the private key to yourself.
2. Log in once as root with the provider's password, update and set the time zone:
   ```
   apt update && apt -y upgrade
   timedatectl set-timezone Europe/Berlin     # or yours
   free -h
   ```
   If `Swap:` shows 0, add 2 GB (the image build needs a lot of memory for a few minutes):
   ```
   fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
   echo '/swapfile none swap sw 0 0' >> /etc/fstab
   ```
3. Create your own user (here `deploy`; pick any name) and give it your public key:
   ```
   adduser deploy
   usermod -aG sudo deploy
   install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
   nano /home/deploy/.ssh/authorized_keys      # paste the one line starting with ssh-ed25519
   chown deploy:deploy /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys
   ```
4. **Test it** from a new terminal on your PC: `ssh deploy@SERVER-IP` must log you in with the key (asking for the key's passphrase, not the account password), and `sudo whoami` must print `root`. Keep that session open.
5. Only then switch off root login and passwords:
   ```
   sudo tee /etc/ssh/sshd_config.d/00-hardening.conf <<'EOF'
   PermitRootLogin no
   PasswordAuthentication no
   EOF
   sudo sshd -t && sudo systemctl restart ssh
   ```
   (The `00` matters: for SSH the first value wins, and a vendor file such as `50-cloud-init.conf` may allow passwords.) Open another new session to check the key still works before you close the old one. Locked out? Log in through the provider's console with the password and set `PasswordAuthentication yes` in that file again.
6. Firewall:
   ```
   sudo ufw allow OpenSSH
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw allow 443/udp        # HTTP/3
   sudo ufw enable
   ```
   Docker publishes ports around ufw, so ufw is not what protects the game's internals: the database has no published port and the app's port 8461 is bound to `127.0.0.1` only. Only Caddy's 80/443 face the internet.

Ubuntu installs security updates by itself (`unattended-upgrades`) but does not reboot; reboot now and then when `/var/run/reboot-required` exists, and tell your players first.

## 3. Install Docker

Docker's official apt repository, as your user:
```
sudo apt-get update && sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker deploy
```
Log out and in again, then `docker run --rm hello-world` and `docker compose version` should work.

Two shortcuts save a lot of typing; add them to `~/.bashrc` and run `source ~/.bashrc`:
```
alias bf='docker compose -f /opt/bannerfall/compose.prod.yaml'
alias bfa='docker compose -f /opt/bannerfall/compose.prod.yaml exec app bannerfall-admin'
```

## 4. Get the code

Each release is a tag (`v1.2.1`, …). Check out the newest one:
```
sudo mkdir -p /opt/bannerfall && sudo chown deploy:deploy /opt/bannerfall
git clone https://github.com/Remph/bannerfall.git /opt/bannerfall
cd /opt/bannerfall
git tag --sort=-v:refname | head -3
git checkout v1.2.1                        # the newest tag from the list
```

## 5. Configure `.env`

```
cd /opt/bannerfall
cp .env.example .env
chmod 600 .env
sed -i "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$(openssl rand -hex 24)/" .env
sed -i "s/^BANNERFALL_ADMIN_TOKEN=.*/BANNERFALL_ADMIN_TOKEN=$(openssl rand -hex 24)/" .env
sed -i "s/^BANNERFALL_DOMAIN=.*/BANNERFALL_DOMAIN=play.example.com/" .env
sed -i "s|^BANNERFALL_IMAGE=.*|BANNERFALL_IMAGE=bannerfall:1.2.1|" .env
nano .env
```
Check in `nano` that these are filled:

| Line | Value |
|---|---|
| `POSTGRES_PASSWORD` | the random value; set it **before the first start** (the database keeps the password it was created with) |
| `BANNERFALL_ADMIN_TOKEN` | the random value (at least 32 characters; compose and the server refuse to start without it) |
| `COMPOSE_PROFILES` | `caddy` (the template's default) |
| `BANNERFALL_DOMAIN` | your realm's name |
| `BANNERFALL_IMAGE` | the image you build next, same version as the tag |
| `TZ` | your time zone |
| `BANNERFALL_OPERATOR_NAME`, `BANNERFALL_OPERATOR_CONTACT` | who runs the realm and how players reach you (usually an email address); shown on the privacy page (`/privacy`), linked under the login and register forms. Empty, the page only explains what the game stores |

The privacy page describes what Bannerfall itself stores; it is a starting point, not legal advice. Whether your realm needs more (an imprint, a longer privacy notice, details about your hosting provider) depends on your country and on who plays. It tells players that neither the game nor Caddy logs their address; if you put another proxy in front, switch its access log off.

Leave the port lines commented out unless you need them (README, *Ports*), and do **not** add `BANNERFALL_CHEATS_ENABLED`. Copy the two secrets into your password manager (`grep -E '^(POSTGRES_PASSWORD|BANNERFALL_ADMIN_TOKEN)=' .env`) and never put `.env` anywhere public.

## 6. Build the image and start

The first build downloads Node, pnpm, Gradle and all dependencies and takes 5 to 15 minutes (run it in `tmux` if your connection is shaky):
```
cd /opt/bannerfall
docker build -t bannerfall:1.2.1 --build-arg VERSION=1.2.1 .
```
If it dies with `Killed`, check `free -h` (swap, step 2) and run it again; finished layers are cached.

DNS must be right before the first start (`nslookup play.example.com`), or Caddy's certificate request fails and Let's Encrypt rate-limits repeated failures. Then start. The image exists only on this machine, so do **not** run `pull`:
```
bf up -d
bf ps                          # app and db "healthy" within about a minute, caddy "running"
bf logs caddy | tail -n 40     # look for "certificate obtained successfully"
```
Open `https://play.example.com`: the login page with a padlock.

## 7. Prove backup and restore while the world is empty

1. Set the first world settings (they live in the database, so the backup holds them):
   ```
   bfa world set invite-code <your-secret-code>
   bfa world set speed 6                      # default 1; 6 suits a small group playing a week or two
   bfa world set beginner-protection-hours 36 # default 72
   ```
2. `ops/bannerfall-backup` prints `backup ok: backups/bannerfall-…sql.gz`. Call it backup A.
3. Register a throw-away player in the browser; `bfa players` shows it.
4. `ops/bannerfall-restore backups/bannerfall-<A>.sql.gz`, type `restore`.
5. `bfa players` is empty again, `bfa world` still shows your settings, `bfa check` reports no problems.

## 8. Daily backups and an off-server copy

```
mkdir -p /opt/bannerfall/backups
crontab -e
```
Add (daily at 03:17):
```
17 3 * * * /opt/bannerfall/ops/bannerfall-backup >> /opt/bannerfall/backups/backup.log 2>&1
```
A backup on the same disk does not survive losing the server: copy the newest files from `/opt/bannerfall/backups` to your PC now and then (`scp`, `rsync` or your SSH client's file browser), and always before an update. Provider snapshots are a cheap extra undo before big updates.

## 9. Open the world

Follow the README section *First steps in a new world*. In short: register yourself and make yourself admin (`bfa player YourName role admin --yes`, then log in again), check the `/admin` page (tab World; map size, barbarians and NPC lords are fixed once the world is open), give your players the address and the invite code, and set the opening time: `bfa world open --at "2026-11-07 18:00"`. At that moment everybody registered gets a village.

A short daily look keeps a realm healthy: `bfa status`, `bfa events failed`, `bfa check`, `bfa suspicious`, `bfa logs --lines 50`.

Reboot once before the players arrive (`sudo reboot`): after two minutes `bf ps` shows all three containers up again and the site loads. Every restart logs players out (sessions live in memory), so announce it with `bfa broadcast "Restart in 5 minutes"`.

## 10. Updating to a new release

```
cd /opt/bannerfall
ops/bannerfall-backup                      # always first; it must print "backup ok"
git status --short                         # must print nothing
git fetch --tags origin
git checkout v1.2.2                        # the new release tag
docker build -t bannerfall:1.2.2 --build-arg VERSION=1.2.2 .
nano .env                                  # BANNERFALL_IMAGE=bannerfall:1.2.2
bfa broadcast "Update in 5 minutes"        # wait, then:
bf up -d
bfa status                                 # shows the new version
```
Since 1.4.1 one command does all of this: `ops/bannerfall-update 1.4.2` (the new version). It stops at the first step that fails. If the new version doesn't come up, it goes back to the old image by itself when no migration ran, or prints how to restore the backup it just took (README *Deploying*, `--help` for the options). With an image you build on the server, add `--build`.

Read the release notes first: some updates switch new features on in a running world (the README's *Updating a running world* tables list the settings). The database migrates forward only, so going back means putting the old image back in `.env` **and** restoring the backup you took before the update. Keep old images until you are sure (`docker image prune` later).

## 11. When something is wrong

| Symptom | Check |
|---|---|
| `bf up -d` says `set POSTGRES_PASSWORD in .env` (or the token) | `.env` is not in `/opt/bannerfall` or the value is empty. |
| `bf up -d` tries to pull `bannerfall:…` and fails | The image is not built on this server, or `.env` names another tag: `docker images bannerfall`. |
| App keeps restarting | `bf logs app --tail 80`. Token shorter than 32 characters? Database password changed after the first start? |
| Connection refused or timeout | `sudo ufw status` (80, 443), your provider's firewall, `bf ps`. |
| Certificate error, `bf logs caddy` mentions `acme` | DNS must return the server's address and no stale AAAA; port 80 must be reachable from outside. Fix the cause, then `bf restart caddy` once (rate limits). |
| Can't log in, or the page says disconnected | Use `https://` and the realm's exact name (cookies are `Secure`); `bf logs app` for WARN lines. Behind your own proxy see the README (*TrueNAS or another proxy instead of Caddy*). |
| Backup cron silent | The `backups/` folder must exist and your user must be in the `docker` group; read `backups/backup.log`. |
| Disk filling | `df -h`, `docker system df`, `docker builder prune`. |

## 12. Several worlds on one server

Every world is its own copy of the setup above: its own folder, database, `.env`, backups and admin token. One shared Caddy (the folder `edge/` in the repository) holds ports 80 and 443 for all of them and passes each world's domain on to that world's app port. The worlds share nothing else, so restoring, restarting or updating one never touches another. Each extra world needs about 1.3 GB of memory limit (app 768m, database 512m); a 4 GB server fits two worlds, and before a third, look at `docker stats --no-stream` first.

A server with one world doesn't need any of this: keep `COMPOSE_PROFILES=caddy` as in step 5.

### 12.1 Move the first world behind the shared Caddy (once)

The first world's app and database keep running; only the proxy is swapped, so the site is gone for a few seconds.

1. Get the shared Caddy's folder, on the same tag as your world:
   ```
   sudo mkdir -p /opt/bannerfall-edge && sudo chown deploy:deploy /opt/bannerfall-edge
   git clone https://github.com/Remph/bannerfall.git /opt/bannerfall-edge
   cd /opt/bannerfall-edge && git checkout v1.5.0          # the tag your world runs
   cd edge && cp sites.caddy.example sites.caddy && nano sites.caddy
   ```
   Keep one block, your first world's: its domain and its `APP_PORT` (8461 unless you changed it). Delete the second example block. `sites.caddy` is this server's list and stays out of git, like `.env`.
2. Create the shared Caddy without starting it, and give it a copy of the first world's certificate, so it serves the same certificate from its first second and the swap doesn't wait for Let's Encrypt:
   ```
   cd /opt/bannerfall-edge/edge && docker compose create
   docker run --rm -v bannerfall_caddy_data:/from:ro -v bannerfall-edge_caddy_data:/to caddy:2.11.7 cp -a /from/. /to/
   ```
   `bannerfall_caddy_data` is the first world's certificate volume (`docker volume ls`); the world's own Caddy keeps its copy for the way back. The shared Caddy renews the certificate itself later. It runs on the host's network, so unlike a world's own Caddy it goes through ufw: check that the rules for 80/tcp, 443/tcp and 443/udp from step 2 are there (`sudo ufw status`).
3. Swap the proxies:
   ```
   bfa broadcast "Short interruption in 5 minutes"         # wait, then:
   sed -i 's/^COMPOSE_PROFILES=caddy/COMPOSE_PROFILES=/' /opt/bannerfall/.env
   bf --profile caddy rm -sf caddy                         # stops the world's own Caddy
   cd /opt/bannerfall-edge/edge && docker compose up -d
   docker compose logs caddy | tail -n 20                  # "serving initial configuration", no error
   ```
   Open the site and log in: the game and the WebSocket work as before.
4. Add two aliases for the shared Caddy to `~/.bashrc`:
   ```
   alias bfe='docker compose -f /opt/bannerfall-edge/edge/compose.yaml'
   alias bfe-reload='docker compose -f /opt/bannerfall-edge/edge/compose.yaml exec caddy caddy reload --config /etc/caddy/Caddyfile'
   ```

**Going back:** `bfe down`, put `COMPOSE_PROFILES=caddy` back into `/opt/bannerfall/.env` and `bf up -d`.

### 12.2 Add a world

1. DNS for the new world's name first (step 1), for example `two.example.com`.
2. A folder of its own, on the same tag as the first world:
   ```
   sudo mkdir -p /opt/bannerfall-two && sudo chown deploy:deploy /opt/bannerfall-two
   git clone https://github.com/Remph/bannerfall.git /opt/bannerfall-two
   cd /opt/bannerfall-two && git checkout v1.5.0
   ```
3. `.env` as in step 5, with new secrets, and these differences:

   | Line | Value |
   |---|---|
   | `COMPOSE_PROJECT_NAME` | a name of its own, for example `bannerfall-two` (add the line). It names the world's containers and volumes; never reuse another world's name, or the two would share a database |
   | `COMPOSE_PROFILES` | empty (the shared Caddy serves the world) |
   | `APP_PORT` | a port of its own: `8462`, the next world `8463` (uncomment the line) |
   | `BANNERFALL_DOMAIN` | the new world's name |
   | `BANNERFALL_IMAGE` | the same image as the first world; there's nothing to build again |
4. Aliases for this world (`~/.bashrc`), then start it:
   ```
   alias bf2='docker compose -f /opt/bannerfall-two/compose.prod.yaml'
   alias bfa2='docker compose -f /opt/bannerfall-two/compose.prod.yaml exec app bannerfall-admin'
   ```
   Before the first start, `bf2 config | head -n 1` must print `name: bannerfall-two`, the name from `.env`. If it prints `name: bannerfall`, stop: `.env` lacks the line, and starting would take over the first world's containers. Then `bf2 up -d`, `bf2 ps`: `app` and `db` become healthy; there is no `caddy` in this world.
5. Add its block to `/opt/bannerfall-edge/edge/sites.caddy` (its domain and `APP_PORT`), then `bfe-reload`. The new site gets its certificate within seconds; the other worlds are not interrupted.
6. Steps 7 to 9 with this world's folder and aliases: the backup drill (`/opt/bannerfall-two/ops/bannerfall-backup`), a cron line a few minutes after the first world's (`27 3 * * * /opt/bannerfall-two/ops/bannerfall-backup >> /opt/bannerfall-two/backups/backup.log 2>&1`), and opening the world with `bfa2`.

### 12.3 Updating several worlds

Update every world to the same release in one go, the newest world first and the first world last, so a problem shows up before it reaches your oldest world. In each world's folder it is step 10 as usual, with that world's aliases; build or pull the image only once. Then bring the shared Caddy to the same tag:
```
cd /opt/bannerfall-edge && git fetch --tags origin && git checkout v1.5.1
bfe up -d && bfe-reload                    # picks up a changed Caddy image or Caddyfile
```

### 12.4 When something is wrong

| Symptom | Check |
|---|---|
| `bfe up -d` fails with "address already in use" | A world still runs its own Caddy: set `COMPOSE_PROFILES=` in that world's `.env` and `docker compose -f <its compose.prod.yaml> --profile caddy rm -sf caddy`. |
| The shared Caddy keeps restarting | `bfe logs caddy`: "File to import not found: sites.caddy" means step 12.1.1 is missing; any other line names the mistake in `sites.caddy`. |
| One world answers 502 | That world's app is down or the port in `sites.caddy` is wrong: `bf2 ps`, and compare with `APP_PORT` in its `.env`. |

## 13. Log in through an account server (optional)

By default every realm has its own accounts: players register with the invite code and a
password. A realm can instead (or as well) let players log in with an account at an OpenID
Connect provider, for example one account server for several realms or a Keycloak you already
run. The realm only needs the provider's issuer address and a client registered there.

At the provider, register a confidential client for the realm:

- redirect URI `https://play.example.com/login/oauth2/code/accounts` (your realm's domain);
- grant type authorization code, PKCE required, scopes `openid profile`, no consent screen needed;
- client authentication `client_secret_basic` (or `client_secret_post`);
- the ID token must carry `preferred_username`: it becomes the player name on the realm, so it
  must follow the game's rule (3 to 24 letters, digits, `_` and `-`);
- optional, but recommended: back-channel logout to
  `https://play.example.com/api/auth/backchannel-logout`, so a ban or a deleted account at the
  provider ends the player's open sessions on the realm at once.

Then in the realm's `.env`:

| Line | Value |
|---|---|
| `BANNERFALL_ACCOUNT_SERVER_ISSUER` | the provider's issuer, e.g. `https://accounts.example.com` (https; the realm reads `/.well-known/openid-configuration` there) |
| `BANNERFALL_ACCOUNT_SERVER_CLIENT_ID`, `..._CLIENT_SECRET` | the client from above |
| `BANNERFALL_ACCOUNT_SERVER_NAME` | the name on the button, "Log in with example.com"; empty = the issuer's host |
| `BANNERFALL_ACCOUNT_SERVER_MODE` | `OFF` (default: everything as before), `LINKING` (the button next to the usual login; existing players can link their account under "Change password", after which their password here no longer works) or `REQUIRED` (only the button; no passwords, no registration on the realm) |
| `BANNERFALL_ACCOUNT_SERVER_CAREER` | `false` (default). `true` only for an account server that keeps careers across realms: every few minutes the realm then sends it, for each player who logs in through it, the name, points, villages, rank, achievements and, once the world has ended, the result (`POST <issuer>/realm-api/career`, HTTP Basic with the client id and secret). Ordinary OpenID Connect providers such as Keycloak don't take it, so leave it off for them. The realm's privacy page says so when it is on |

`docker compose -f compose.prod.yaml up -d` to apply it. Things to know:

- A first login through the provider asks for the realm's invite code once, then creates the
  player. Links like `https://play.example.com/join?invite=CODE` fill it in.
- If the provider is down, nobody can log in through it; open sessions keep working. The realm
  still starts and checks the provider again at the next login.
- Roles, bans, deleting players and everything else in the realm's admin tools stay per realm.
  A realm-side ban also stops a player who logs in through the provider.
- Switching back to `OFF` hides the button. Players who registered through the provider (or
  linked their account) have no password on the realm then; set one for them with
  `bannerfall-admin player <name> reset-password` (or the admin page) if they should keep playing:
  that makes them local accounts again. In `REQUIRED` mode the reset refuses players of the
  provider: a local account couldn't log in there.
- Switching `LINKING` to `REQUIRED` locks out every player who hasn't linked yet (no password
  login any more). Let them link first, or switch back to `LINKING` for them.
- Nothing in the database changes for existing players: switching a realm to `LINKING` is safe
  for a running world.

## 14. Push notifications (optional)

Players can get a notification on their phone or computer while the game is closed: attacks on
the way, lost villages, private messages, guild invitations, the victory clock and the end of the
world, and (if they switch it on) an empty build queue. They turn it on per device under
"Notifications" in the menu; on an iPhone or iPad only from the home screen (iOS 16.4 or newer).

The realm needs a key pair of its own, made once:

```
docker compose -f compose.prod.yaml exec -T app bannerfall-admin push keys >> .env
docker compose -f compose.prod.yaml up -d
```

That appends `BANNERFALL_PUSH_PUBLIC_KEY` and `BANNERFALL_PUSH_PRIVATE_KEY` to `.env`. Keep them
with the rest of `.env`: they are not in the database backups. Things to know:

- Without the two lines the realm sends nothing and the menu says so. Only one of them, or two that
  don't belong together, stops the app at the start.
- Notifications go out through the push service of each player's browser (Google, Mozilla, Apple,
  Microsoft), encrypted so the service can't read them. The server must be able to reach those over
  https; nothing else is needed and nothing costs money.
- The push services like to know who sends: `BANNERFALL_PUSH_SUBJECT` (`mailto:` or an https
  address), by default `BANNERFALL_OPERATOR_CONTACT` if it is an email address.
- A new key pair makes every device sign up again, which happens by itself the next time the
  player opens the game. Several worlds each have their own pair.
- `bannerfall-admin status` shows how many devices get notifications.
