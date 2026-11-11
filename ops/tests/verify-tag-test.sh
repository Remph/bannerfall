#!/usr/bin/env bash
# Tests ops/bannerfall-verify-tag with real git and throwaway SSH keys (nothing of the project's):
#   ops/tests/verify-tag-test.sh
# Needs git and ssh-keygen. Each case makes a checkout with a pinned key and a tag signed in some
# way, and checks what the script said and did.
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
ssh-keygen -q -t ed25519 -N '' -C release -f "$work/release_key"
ssh-keygen -q -t ed25519 -N '' -C stranger -f "$work/stranger_key"
release_pub="$(cut -d' ' -f1,2 "$work/release_key.pub")"
digest="sha256:$(printf 'a%.0s' $(seq 1 64))"

# A checkout with the script, a signers file and a commit; $1 is the signers file's content.
setup() {
  rm -rf "$work/repo"
  mkdir -p "$work/repo/ops"
  cp "$here/../bannerfall-verify-tag" "$work/repo/ops/"
  printf '%s\n' "$1" > "$work/repo/ops/release-signers"
  git -C "$work/repo" init -q
  git -C "$work/repo" add -A
  git -C "$work/repo" -c user.name=t -c user.email=t@example.com commit -q -m one
}

# tag <name> <signing key file or -> [message lines...]: an annotated tag, signed with the key.
tag() {
  local name="$1" key="$2"
  shift 2
  local args=()
  for line in "$@"; do args+=(-m "$line"); done
  if [ "$key" = - ]; then
    git -C "$work/repo" -c user.name=t -c user.email=t@example.com tag -a "$name" "${args[@]}"
  else
    git -C "$work/repo" -c user.name=t -c user.email=t@example.com -c gpg.format=ssh \
      -c user.signingkey="$key" tag -s "$name" "${args[@]}"
  fi
}

run_script() { # args; sets $out (stdout), $err (stderr) and $status
  out="$(cd "$work/repo" && ops/bannerfall-verify-tag "$@" 2> "$work/err")"
  status=$?
  err="$(cat "$work/err")"
}

signers_line="bannerfall-releases namespaces=\"git\" $release_pub"

setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@$digest"
run_script v2.0.1
check "signed by the pinned key: exit 0" '[ "$status" -eq 0 ]'
check "signed by the pinned key: verified=yes" 'grep -qx "verified=yes" <<< "$out"'
check "signed by the pinned key: names the image by digest" 'grep -qx "image=ghcr.io/example/bannerfall@$digest" <<< "$out"'

setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1"
run_script v2.0.1
check "signed, no image line: exit 0, no image" '[ "$status" -eq 0 ] && ! grep -q "^image=" <<< "$out"'

setup "$signers_line"
tag v2.0.1 "$work/stranger_key" "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@$digest"
run_script v2.0.1
check "signed by another key: exit 1, says not verified" '[ "$status" -eq 1 ] && grep -q "NOT VERIFIED" <<< "$err"'
check "signed by another key: prints nothing for the caller" '[ -z "$out" ]'

setup "$signers_line"
tag v2.0.1 - "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@$digest"
run_script v2.0.1
check "annotated but unsigned: exit 1" '[ "$status" -eq 1 ] && grep -q "NOT VERIFIED" <<< "$err"'

setup "$signers_line"
git -C "$work/repo" tag v2.0.1
run_script v2.0.1
check "lightweight tag: exit 1" '[ "$status" -eq 1 ] && grep -q "not a signed tag" <<< "$err"'

setup "$signers_line"
run_script v2.0.1
check "no such tag: exit 1" '[ "$status" -eq 1 ] && grep -q "no tag v2.0.1" <<< "$err"'

setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@sha256:abc"
run_script v2.0.1
check "a malformed image line: exit 1" '[ "$status" -eq 1 ] && grep -q "form this script doesn" <<< "$err"'

setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@$digest" "Image: ghcr.io/evil/bannerfall@$digest"
run_script v2.0.1
check "two image lines: exit 1" '[ "$status" -eq 1 ] && grep -q "more than one image" <<< "$err"'

# A signed tag of an older release filed under a newer name: the signature is good, the name is not.
setup "$signers_line"
tag v2.0.0 "$work/release_key" "Bannerfall 2.0.0" "Image: ghcr.io/example/bannerfall@$digest"
git -C "$work/repo" update-ref refs/tags/v2.0.9 "$(git -C "$work/repo" rev-parse refs/tags/v2.0.0)"
run_script v2.0.9
check "an old signed tag under a newer name: exit 1" '[ "$status" -eq 1 ] && grep -q "says it is v2.0.0, not v2.0.9" <<< "$err"'
check "an old signed tag under a newer name: prints nothing for the caller" '[ -z "$out" ]'

# An Image line added after the signature is not signed, so it must not count.
setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1"
{ git -C "$work/repo" cat-file tag v2.0.1; echo "Image: ghcr.io/evil/bannerfall@$digest"; } > "$work/forged"
git -C "$work/repo" update-ref refs/tags/v2.0.1 "$(git -C "$work/repo" hash-object -w -t tag "$work/forged")"
run_script v2.0.1
check "an image line after the signature: still verified, but names no image" \
  '[ "$status" -eq 0 ] && grep -qx "verified=yes" <<< "$out" && ! grep -q "^image=" <<< "$out"'

# Nothing pinned yet: a warning, and the image is read from the tag all the same.
setup "# no key yet"
tag v2.0.1 - "Bannerfall 2.0.1" "Image: ghcr.io/example/bannerfall@$digest"
run_script v2.0.1
check "nothing pinned: exit 0, verified=no, warned" \
  '[ "$status" -eq 0 ] && grep -qx "verified=no" <<< "$out" && grep -q "WARNING" <<< "$err"'
check "nothing pinned: still names the image" 'grep -qx "image=ghcr.io/example/bannerfall@$digest" <<< "$out"'

setup "# no key yet"
git -C "$work/repo" tag v2.0.1
run_script v2.0.1
check "nothing pinned, lightweight tag: exit 0, verified=no" '[ "$status" -eq 0 ] && grep -qx "verified=no" <<< "$out"'

setup "# no key yet"
tag v2.0.1 - "Bannerfall 2.0.1"
out="$(cd "$work/repo" && BANNERFALL_REQUIRE_SIGNED=1 ops/bannerfall-verify-tag v2.0.1 2> "$work/err")"
status=$?
check "nothing pinned with BANNERFALL_REQUIRE_SIGNED=1: exit 1" '[ "$status" -eq 1 ]'

# A pin outside the checkout wins over the file in it.
setup "# no key yet"
printf '%s\n' "$signers_line" > "$work/outside"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1"
out="$(cd "$work/repo" && BANNERFALL_RELEASE_SIGNERS="$work/outside" ops/bannerfall-verify-tag v2.0.1 2> "$work/err")"
status=$?
check "a pin outside the checkout is used" '[ "$status" -eq 0 ] && grep -qx "verified=yes" <<< "$out"'

setup "$signers_line"
tag v2.0.1 "$work/release_key" "Bannerfall 2.0.1"
run_script latest
check "not a release tag name: exit 2" '[ "$status" -eq 2 ]'

if [ "$failures" -eq 0 ]; then echo "all passed"; else echo "$failures failed"; exit 1; fi
