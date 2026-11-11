#!/usr/bin/env bash
# Tests what ops/bannerfall-update does with a release tag: the signature check against the pinned
# key and the image pulled by the digest the tag names. Real git (an "upstream" with signed tags and
# a checkout of it) and throwaway SSH keys, stand-ins for docker and the backup:
#   ops/tests/update-test.sh
# The conditions in check are strings on purpose (evaluated there); out and status are read by them.
# shellcheck disable=SC2016,SC2034
set -uo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
failures=0

check() { # name, command that succeeds when the case is right (evaluated here)
  if eval "$2"; then echo "ok   $1"; else echo "FAIL $1"; failures=$((failures + 1)); fi
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_SYSTEM=/dev/null
export GIT_AUTHOR_NAME=t GIT_AUTHOR_EMAIL=t@example.com GIT_COMMITTER_NAME=t GIT_COMMITTER_EMAIL=t@example.com
ssh-keygen -q -t ed25519 -N '' -C release -f "$work/release_key"
ssh-keygen -q -t ed25519 -N '' -C stranger -f "$work/stranger_key"
release_pub="$(cut -d' ' -f1,2 "$work/release_key.pub")"
digest="sha256:$(printf 'b%.0s' $(seq 1 64))"
image="ghcr.io/example/bannerfall"

# tag_message <digest line image name>: the lines of the 2.0.1 tag's message
# case_setup <signers: pinned|empty> <how 2.0.1 is tagged: signed|unsigned|stranger|lightweight>
#            <image line in the tag: yes|no|other>
case_setup() {
  rm -rf "${work:?}/up" "${work:?}/world" "${work:?}/bin" "${work:?}/state"
  mkdir -p "$work/up/ops" "$work/bin" "$work/state"
  cp "$here/../bannerfall-update" "$here/../bannerfall-verify-tag" "$work/up/ops/"
  printf '#!/usr/bin/env bash\necho "backup ok: backups/x.sql.gz (1K)"\n' > "$work/up/ops/bannerfall-backup"
  chmod +x "$work"/up/ops/*
  : > "$work/up/compose.prod.yaml"
  if [ "$1" = pinned ]; then
    echo "bannerfall-releases namespaces=\"git\" $release_pub" > "$work/up/ops/release-signers"
  else
    echo "# no key yet" > "$work/up/ops/release-signers"
  fi
  git -C "$work/up" init -q
  git -C "$work/up" add -A
  git -C "$work/up" commit -q -m two-zero-zero
  git -C "$work/up" tag v2.0.0
  echo patch > "$work/up/patch.txt"
  git -C "$work/up" add -A
  git -C "$work/up" commit -q -m two-zero-one
  local args=(-m "Bannerfall 2.0.1")
  case "$3" in
    yes) args+=(-m "Image: $image@$digest") ;;
    other) args+=(-m "Image: ghcr.io/elsewhere/bannerfall@$digest") ;;
  esac
  case "$2" in
    signed) git -C "$work/up" -c gpg.format=ssh -c user.signingkey="$work/release_key" tag -s v2.0.1 "${args[@]}" ;;
    stranger) git -C "$work/up" -c gpg.format=ssh -c user.signingkey="$work/stranger_key" tag -s v2.0.1 "${args[@]}" ;;
    unsigned) git -C "$work/up" tag -a v2.0.1 "${args[@]}" ;;
    lightweight) git -C "$work/up" tag v2.0.1 ;;
  esac
  git clone -q "$work/up" "$work/world"
  git -C "$work/world" -c advice.detachedHead=false checkout -q v2.0.0
  echo "BANNERFALL_IMAGE=$image:2.0.0" > "$work/world/.env"
  echo 2.0.0 > "$work/state/version"
  : > "$work/calls"
  # docker stand-in: a world that runs the version in state/version and takes the one .env names
  # at the next "compose up -d"
  cat > "$work/bin/docker" <<STUB
#!/usr/bin/env bash
case "\$*" in
  *"ps --status running --services"*) echo app ;;
  *"ps -q app"*) echo fakeid ;;
  "inspect -f"*) echo healthy ;;
  *"bannerfall-admin status --json"*) echo "{\\"version\\" : \\"\$(cat "$work/state/version")\\"}" ;;
  *"bannerfall-admin status"*|*"bannerfall-admin check"*) ;;
  *"psql"*) echo 5 ;;
  *"up -d"*)
    echo "docker \$*" >> "$work/calls"
    sed -n 's/^BANNERFALL_IMAGE=[^:]*:\\([0-9.]*\\).*/\\1/p' .env > "$work/state/version" ;;
  *) echo "docker \$*" >> "$work/calls" ;;
esac
STUB
  chmod +x "$work/bin/docker"
}

run_script() { # args for the script; sets $out and $status
  out="$(cd "$work/world" && PATH="$work/bin:$PATH" ops/bannerfall-update "$@" 2>&1 < /dev/null)"
  status=$?
}

case_setup pinned signed yes
run_script 2.0.1 --yes --wait 0
check "signed tag: exit 0" '[ "$status" -eq 0 ]'
check "signed tag: says the signature was checked" 'grep -q "signature checked" <<< "$out"'
check "signed tag: pulls by the digest of the tag" 'grep -qx "docker pull --quiet $image:2.0.1@$digest" "$work/calls"'
check "signed tag: .env names the digest" 'grep -qx "BANNERFALL_IMAGE=$image:2.0.1@$digest" "$work/world/.env"'
check "signed tag: the release is checked out" '[ "$(git -C "$work/world" describe --tags --exact-match)" = v2.0.1 ]'

case_setup pinned unsigned yes
run_script 2.0.1 --yes --wait 0
check "unsigned tag: exit 1, stopped" '[ "$status" -eq 1 ] && grep -q "UPDATE STOPPED" <<< "$out"'
check "unsigned tag: nothing pulled, nothing checked out, .env as before" \
  '! grep -q "docker pull" "$work/calls" && [ "$(git -C "$work/world" describe --tags --exact-match)" = v2.0.0 ] && grep -qx "BANNERFALL_IMAGE=$image:2.0.0" "$work/world/.env"'

case_setup pinned stranger yes
run_script 2.0.1 --yes --wait 0
check "tag of another key: exit 1, nothing pulled" '[ "$status" -eq 1 ] && ! grep -q "docker pull" "$work/calls"'

case_setup pinned lightweight yes
run_script 2.0.1 --yes --wait 0
check "lightweight tag: exit 1, nothing pulled" '[ "$status" -eq 1 ] && ! grep -q "docker pull" "$work/calls"'

case_setup empty unsigned yes
run_script 2.0.1 --yes --wait 0
check "nothing pinned: goes on, and says the signature was not checked" \
  '[ "$status" -eq 0 ] && grep -q "SIGNATURE NOT CHECKED" <<< "$out"'
check "nothing pinned: still pulls by the digest" 'grep -qx "docker pull --quiet $image:2.0.1@$digest" "$work/calls"'

case_setup pinned signed no
run_script 2.0.1 --yes --wait 0
check "no digest in the tag: warns, pulls by the tag" \
  '[ "$status" -eq 0 ] && grep -q "names no image digest" <<< "$out" && grep -qx "docker pull --quiet $image:2.0.1" "$work/calls"'

case_setup pinned signed other
run_script 2.0.1 --yes --wait 0
check "digest of another image: warns, pulls the own one by tag" \
  '[ "$status" -eq 0 ] && grep -q "you pull $image" <<< "$out" && grep -qx "docker pull --quiet $image:2.0.1" "$work/calls"'

case_setup pinned signed yes
echo "BANNERFALL_IMAGE=$image:2.0.0@sha256:$(printf 'c%.0s' $(seq 1 64))" > "$work/world/.env"
run_script 2.0.1 --yes --wait 0
check "a digest in .env already: the repo is still found" \
  '[ "$status" -eq 0 ] && grep -qx "docker pull --quiet $image:2.0.1@$digest" "$work/calls"'

if [ "$failures" -eq 0 ]; then echo "all passed"; else echo "$failures failed"; exit 1; fi
