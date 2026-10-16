#!/usr/bin/env bash
# Runtime secrets are read by Compose only; never source or print the env file.
set -euo pipefail
set +x
umask 077

fail() { printf '%s\n' "$1" >&2; exit 1; }
[[ $# == 2 ]] || fail 'Usage: deploy.sh <40-character SHA> <archive>'
sha=$1
archive=$2
[[ $sha =~ ^[0-9a-f]{40}$ ]] || fail 'Invalid SHA: expected 40 lowercase hexadecimal characters'
[[ -f $archive && -r $archive ]] || fail 'Release archive is missing or unreadable'
# Resolve before changing into the release directory.
archive=$(cd -- "$(dirname -- "$archive")" && pwd)/$(basename -- "$archive")
runtime_env="$HOME/.config/partner-portal/staging.env"
[[ -f $runtime_env && -r $runtime_env && ! -L $runtime_env ]] || fail 'Required runtime env file is missing or unreadable'
python3 - "$runtime_env" <<'PY' >/dev/null 2>&1 || fail 'Required runtime env must be owned by deployment user with mode 0600'
import os, stat, sys
s = os.stat(sys.argv[1])
if s.st_uid != os.getuid() or stat.S_IMODE(s.st_mode) != 0o600:
    raise ValueError("unsafe env permissions")
PY

releases=${STAGING_RELEASES_DIR:-/opt/partner-portal/releases}
[[ $releases == /* && ! -L $releases ]] || fail 'Release directory must be an absolute directory, not a symlink'
mkdir -p -- "$releases"
# Serialize manual invocations too. A stale lock after SIGKILL needs operator review.
lock="$releases/.deploy-lock"
mkdir -- "$lock" 2>/dev/null || fail 'Another deployment is running (or a stale deployment lock exists)'
staging=''
cleanup() {
  [[ -z $staging ]] || rm -rf -- "$staging"
  rmdir -- "$lock" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
release="$releases/$sha"
[[ ! -L $release ]] || fail 'Release directory must not be a symlink'
if [[ ! -d $release ]]; then
  staging=$(mktemp -d "$releases/.extract-XXXXXXXX")
  # Validate the entire archive before extracting anything; links/devices and
  # runtime env files are forbidden. Python avoids tar's path traversal pitfalls.
  python3 - "$archive" "$staging" <<'PY' >/dev/null 2>&1 || fail 'Invalid release archive'
import pathlib, sys, tarfile
with tarfile.open(sys.argv[1]) as archive:
    members = archive.getmembers()
    for member in members:
        path = pathlib.PurePosixPath(member.name)
        if path.is_absolute() or '..' in path.parts or not (member.isdir() or member.isfile()):
            raise ValueError("unsafe archive member")
        if any(part == '.env' or (part.startswith('.env.') and not part.endswith('.example')) for part in path.parts):
            raise ValueError("runtime env in archive")
    archive.extractall(sys.argv[2], members=members)
if not pathlib.Path(sys.argv[2], 'compose.yaml').is_file():
    raise ValueError("missing compose.yaml")
PY
  mv -- "$staging" "$release"
  staging=''
fi
[[ -f $release/compose.yaml ]] || fail 'Release archive must contain compose.yaml at its root'
cd -- "$release"
compose=(docker compose --project-name partner-portal-staging --env-file "$runtime_env" --file "$release/compose.yaml")
health_status() {
  # Only service/state/health: no logs, commands, env or arbitrary error output.
  "${compose[@]}" ps --all --format 'table {{.Service}}\t{{.State}}\t{{.Health}}' 2>/dev/null || true
}
"${compose[@]}" config --quiet >/dev/null 2>&1 || fail 'Invalid runtime configuration; containers were not changed'
if ! "${compose[@]}" up --build --detach --wait >/dev/null 2>&1; then
  health_status
  fail 'Deployment failed readiness checks'
fi
binding=$("${compose[@]}" port frontend 80 2>/dev/null) || { health_status; fail 'Cannot determine frontend smoke address'; }
# Resolve the published address rather than executing/parsing the runtime env.
host=${binding%:*}
port=${binding##*:}
[[ $host != 0.0.0.0 ]] || host=127.0.0.1
[[ $host != '[::]' && $host != '::' ]] || host='[::1]'
if [[ ! $port =~ ^[0-9]+$ ]] || ! curl --fail --silent --output /dev/null --connect-timeout 5 --max-time 15 --retry 5 --retry-delay 2 "http://$host:$port/" >/dev/null 2>&1; then
  health_status
  fail 'Frontend smoke check failed'
fi
# Keep current plus three previous successful activations; failed attempts
# must never displace a known-good rollback release.
# Only release directories are touched; Docker owns and preserves data volumes.
python3 - "$releases" "$sha" <<'PY' >/dev/null 2>&1 || { health_status; fail 'Release is running but old release cleanup failed'; }
import pathlib, re, shutil, sys
root = pathlib.Path(sys.argv[1])
current = root / sys.argv[2]
successes = root / '.successful-releases'
successes.mkdir(exist_ok=True)
# Record success separately from archive-controlled release directory mtimes.
(successes / current.name).touch()
previous = [p for p in root.iterdir() if p.name != current.name and re.fullmatch('[0-9a-f]{40}', p.name) and not p.is_symlink() and p.is_dir()]
known_good = sorted((p for p in previous if (successes / p.name).is_file()), key=lambda p: (successes / p.name).stat().st_mtime_ns, reverse=True)
keep = {p.name for p in known_good[:3]}
for directory in previous:
    if directory.name not in keep:
        shutil.rmtree(directory)
        (successes / directory.name).unlink(missing_ok=True)
PY
printf 'Staging release %s is ready\n' "$sha"
