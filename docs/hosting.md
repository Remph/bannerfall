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
