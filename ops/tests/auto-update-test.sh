#!/usr/bin/env bash
# Tests ops/bannerfall-auto-update with stand-ins for docker, git and bannerfall-update:
#   ops/tests/auto-update-test.sh
# Each case sets up a fake world (the running version, the tags on origin, whether the image is
# published) and checks what the script did.
# The conditions in check are strings on purpose (evaluated there); out and status are read by them.
# shellcheck disable=SC2016,SC2034
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../bannerfall-auto-update"
failures=0

check() { # name, command that succeeds when the case is right (evaluated here)
  if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

# case_setup <running version> <tags, space separated> <image published: yes|no> <update result: 0|1>
case_setup() {
  rm -rf "$work"
  mkdir -p "$work/ops" "$work/bin" "$work/edge/.git" "$work/edge/edge"
  cp "$script" "$work/ops/bannerfall-auto-update"
  : > "$work/compose.prod.yaml"
  echo "BANNERFALL_IMAGE=ghcr.io/example/bannerfall:$1" > "$work/.env"
  tr ' ' '\n' <<< "$2" > "$work/tags"
  echo "$3" > "$work/published"
  : > "$work/calls"
  # bannerfall-update stand-in
  printf '#!/usr/bin/env bash\necho "bannerfall-update $*" >> "%s/calls"\n[ ! -e "%s/stopped" ] || echo "UPDATE STOPPED: can not pull" >&2\nexit %s\n' "$work" "$work" "$4" > "$work/ops/bannerfall-update"
  # docker stand-in: compose exec ... status --json, manifest inspect, compose up/reload
  cat > "$work/bin/docker" <<STUB
#!/usr/bin/env bash
case "\$*" in
  *"bannerfall-admin status --json"*) [ ! -e "$work/app_down" ] || exit 1; echo '{"version" : "$1"}' ;;
  "manifest inspect "*) [ "\$(cat "$work/published")" = yes ] ;;
  *) echo "docker \$*" >> "$work/calls" ;;
esac
STUB
  # git stand-in: ls-remote prints the tags, everything else is recorded
  cat > "$work/bin/git" <<STUB
#!/usr/bin/env bash
case "\$*" in
  *ls-remote*) sed 's|^|abc123\trefs/tags/|' "$work/tags" ;;
  *describe*) cat "$work/edge_tag" 2> /dev/null || true ;;
  *checkout*) [ ! -e "$work/edge_fail" ] || exit 1; echo "git \$*" >> "$work/calls" ;;
  *) echo "git \$*" >> "$work/calls" ;;
esac
STUB
  # curl stand-in: the check service's address, with the log as the body
  printf '#!/usr/bin/env bash\necho "curl $*" >> "%s/calls"\n' "$work" > "$work/bin/curl"
  chmod +x "$work"/ops/* "$work"/bin/*
}

run_script() { # args for the script; sets $out and $status
  out="$(cd "$work" && PATH="$work/bin:$PATH" EDGE_DIR="$work/edge" ops/bannerfall-auto-update "$@" 2>&1)"
  status=$?
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

case_setup 1.6.1 "v1.6.0 v1.6.1" yes 0
run_script
check "up to date: exit 0" '[ "$status" -eq 0 ]'
check "up to date: no update" '! grep -q bannerfall-update "$work/calls"'

case_setup 1.6.1 "v1.6.0 v1.6.1 v1.6.2 v1.6.10 v1.6.9" yes 0
run_script
check "patch: exit 0" '[ "$status" -eq 0 ]'
check "patch: picks 1.6.10, not 1.6.9, with --yes and a 300 s warning" \
  'grep -qx "bannerfall-update 1.6.10 --yes --wait 300" "$work/calls"'
check "patch: edge checkout moved to v1.6.10" 'grep -q "git -C .*checkout --quiet v1.6.10" "$work/calls"'
check "patch: edge reloaded" 'grep -q "caddy reload\|edge/compose.yaml up -d" "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.7.0 v2.0.0" yes 0
run_script
check "minor only: exit 0" '[ "$status" -eq 0 ]'
check "minor only: no update" '! grep -q bannerfall-update "$work/calls"'
check "minor only: says to update by hand" 'grep -q "ops/bannerfall-update 2.0.0" <<< "$out"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
touch "$work/auto-update.hold"
run_script
check "hold: exit 0, no update" '[ "$status" -eq 0 ] && ! grep -q bannerfall-update "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" no 0
run_script
check "image not published: exit 0, no update" '[ "$status" -eq 0 ] && ! grep -q bannerfall-update "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 1
run_script
check "failed update: exit 1" '[ "$status" -eq 1 ]'
check "failed update: version remembered" '[ "$(cat "$work/auto-update.failed")" = 1.6.2 ]'
check "failed update: edge untouched" '! grep -q "git -C" "$work/calls"'
: > "$work/calls"
run_script
check "failed before: not tried again" '[ "$status" -eq 0 ] && ! grep -q bannerfall-update "$work/calls"'
printf '%s\n' v1.6.1 v1.6.2 v1.6.3 > "$work/tags"
run_script
check "newer patch after a failure is tried" 'grep -qx "bannerfall-update 1.6.3 --yes --wait 300" "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 1
touch "$work/stopped"
run_script
check "stopped before the restart: exit 1, not marked failed" '[ "$status" -eq 1 ] && [ ! -e "$work/auto-update.failed" ]'
check "stopped before the restart: edge untouched" '! grep -q "git -C" "$work/calls"'
: > "$work/calls"
run_script
check "stopped before the restart: tried again next night" 'grep -qx "bannerfall-update 1.6.2 --yes --wait 300" "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
echo v1.7.0 > "$work/edge_tag"
run_script
check "edge on a newer tag: left alone, update done" \
  '[ "$status" -eq 0 ] && grep -q "bannerfall-update 1.6.2" "$work/calls" && ! grep -q "git -C" "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
touch "$work/edge_fail"
run_script
check "edge move fails: exit 1, says so" '[ "$status" -eq 1 ] && grep -q "THE WORLD RUNS 1.6.2" <<< "$out"'
check "edge move fails: version not marked failed" '[ ! -e "$work/auto-update.failed" ]'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
touch "$work/app_down"
run_script
check "app not running: exit 1, no update" '[ "$status" -eq 1 ] && ! grep -q bannerfall-update "$work/calls"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
echo "BANNERFALL_IMAGE=bannerfall:1.6.1" > "$work/.env"
run_script
check "image built on the server: exit 1, no update" '[ "$status" -eq 1 ] && ! grep -q bannerfall-update "$work/calls"'

export AUTO_UPDATE_PING_URL=https://hc.example/abc/
case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
run_script
check "ping after a good update" 'grep -q "curl .* https://hc.example/abc$" "$work/calls"'
case_setup 1.6.1 "v1.6.1 v1.6.2" yes 1
run_script
check "ping /fail after a failed update" 'grep -q "curl .* https://hc.example/abc/fail$" "$work/calls"'
case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
run_script --dry-run
check "no ping on a dry run" '! grep -q curl "$work/calls"'
unset AUTO_UPDATE_PING_URL

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
run_script --dry-run
check "dry run: exit 0, no update" '[ "$status" -eq 0 ] && ! grep -q bannerfall-update "$work/calls"'
check "dry run: says what it would do" 'grep -q "would update 1.6.1 -> 1.6.2" <<< "$out"'

case_setup 1.6.1 "v1.6.1 v1.6.2" yes 0
sed -i 's/1.6.1/1.6/' "$work/bin/docker"
run_script
check "odd version: stops, no update" '[ "$status" -eq 1 ] && ! grep -q bannerfall-update "$work/calls"'

if [ "$failures" -eq 0 ]; then echo "all passed"; else echo "$failures failed"; exit 1; fi
