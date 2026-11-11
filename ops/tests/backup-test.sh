#!/usr/bin/env bash
# Tests ops/bannerfall-backup with a stand-in for docker (and curl for the check service), real
# gzip and age:
#   ops/tests/backup-test.sh
# The off-site cases need age (https://age-encryption.org) and are skipped without it.
# The conditions in check are strings on purpose (evaluated there); out and status are read by them.
# shellcheck disable=SC2012,SC2016,SC2034
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
failures=0

check() { # name, command that succeeds when the case is right (evaluated here)
  if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

setup() {
  rm -rf "${work:?}/world" "${work:?}/bin"
  mkdir -p "$work/world/ops" "$work/bin"
  cp "$here/../bannerfall-backup" "$work/world/ops/"
  : > "$work/world/compose.prod.yaml"
  : > "$work/calls"
  # docker stand-in: pg_dump prints a dump with an account table
  cat > "$work/bin/docker" <<'STUB'
#!/usr/bin/env bash
printf 'CREATE TABLE public.account (id bigint);\nINSERT INTO account VALUES (1);\n'
STUB
  printf '#!/usr/bin/env bash\necho "curl $*" >> "%s/calls"\n' "$work" > "$work/bin/curl"
  chmod +x "$work"/bin/*
}

run_script() { # sets $out and $status
  out="$(cd "$work/world" && PATH="$work/bin:$PATH" ops/bannerfall-backup 2>&1)"
  status=$?
}

setup
run_script
check "plain backup: exit 0, one dump" '[ "$status" -eq 0 ] && [ "$(ls "$work"/world/backups/bannerfall-*.sql.gz | wc -l)" -eq 1 ]'
check "plain backup: the dump is readable by its owner only" '[ "$(stat -c %a "$work"/world/backups/bannerfall-*.sql.gz)" = 600 ]'
check "plain backup: no off-site words" '! grep -q "off-site" <<< "$out"'

setup
BACKUP_OFFSITE_CMD='true' run_script
check "off-site command without a recipient: refused, nothing dumped" \
  '[ "$status" -eq 2 ] && grep -q "never sent unencrypted" <<< "$out" && [ ! -d "$work/world/backups" ]'

if ! command -v age > /dev/null; then
  echo "skip off-site cases: age is not installed"
else
  age-keygen -o "$work/identity.txt" 2> /dev/null
  recipient="$(age-keygen -y "$work/identity.txt")"
  # the command copies its file to a "second place" (a folder) and records its name
  mkdir -p "$work/other"
  export BACKUP_OFFSITE_CMD='cp "$1" "$OTHER/" && basename "$1" >> "$OTHER/names"'
  export OTHER="$work/other"

  setup
  BACKUP_AGE_RECIPIENT="$recipient" run_script
  copy="$(ls "$work"/other/bannerfall-*.sql.gz.age 2> /dev/null | head -n 1)"
  check "off-site: exit 0 and says so" '[ "$status" -eq 0 ] && grep -q "off-site copy ok" <<< "$out"'
  check "off-site: the copy is age-encrypted, not a gzip file" \
    '[ -n "$copy" ] && head -c 21 "$copy" | grep -q "age-encryption.org" && ! gzip -t "$copy" 2> /dev/null'
  check "off-site: the identity opens it and it is the local dump" \
    'age -d -i "$work/identity.txt" "$copy" | cmp -s - "$(ls "$work"/world/backups/bannerfall-*.sql.gz)"'
  check "off-site: nothing readable is in the second place" '! grep -rq "CREATE TABLE" "$work/other"'
  check "off-site: no leftover in the backup folder" '[ -z "$(ls -A "$work"/world/backups | grep -v "^bannerfall-.*sql.gz$")" ]'
  check "off-site: the plain dump is not sent" '[ "$(ls "$work"/other | grep -vc "\.age$\|^names$")" -eq 0 ]'

  setup
  BACKUP_AGE_RECIPIENT="$recipient" BACKUP_OFFSITE_CMD='exit 3' BACKUP_PING_URL=https://hc.example/abc run_script
  check "off-site fails: exit 1, says so, the local dump stays" \
    '[ "$status" -eq 1 ] && grep -q "OFF-SITE COPY FAILED" <<< "$out" && ls "$work"/world/backups/bannerfall-*.sql.gz > /dev/null'
  check "off-site fails: reported as /fail, no ok ping" \
    'grep -q "curl .* https://hc.example/abc/fail$" "$work/calls" && ! grep -q "curl .* https://hc.example/abc$" "$work/calls"'
  check "off-site fails: no leftover in the backup folder" '[ -z "$(ls -A "$work"/world/backups | grep -v "^bannerfall-.*sql.gz$")" ]'

  setup
  BACKUP_AGE_RECIPIENT="not a key" run_script
  check "a bad recipient: exit 1, local dump stays" '[ "$status" -eq 1 ] && ls "$work"/world/backups/bannerfall-*.sql.gz > /dev/null'

  setup
  BACKUP_AGE_RECIPIENT="$recipient" BACKUP_PING_URL=https://hc.example/abc run_script
  check "off-site ok: pings ok" 'grep -q "curl .* https://hc.example/abc$" "$work/calls"'

  # a recipient without a command changes nothing
  setup
  unset BACKUP_OFFSITE_CMD
  BACKUP_AGE_RECIPIENT="$recipient" run_script
  check "a recipient alone: plain backup" '[ "$status" -eq 0 ] && ! grep -q "off-site" <<< "$out"'
fi

if [ "$failures" -eq 0 ]; then echo "all passed"; else echo "$failures failed"; exit 1; fi
