#!/usr/bin/env bash
# Makes the .deb that jpackage builds install on a machine that has no desktop menu directory.
#
#   desktopApp/packaging/deb/harden-postinst.sh [--in-place] <in.deb> [<out.deb>]
#
# Why: the file association (.enclybak) makes jpackage put `xdg-desktop-menu install ...` into the package's postinst
# (JDK 21 template.postinst, "configure)" branch). xdg-desktop-menu refuses to work when /usr/share/desktop-directories
# does not exist ("No writable system menu directory found"), which is the case on a system without a desktop, for
# example a bare window manager. The install then fails and leaves the package half-configured. Compose Desktop has no
# supported way to change jpackage's maintainer scripts, so the finished .deb is patched here: one line is added to the
# postinst that creates the directory (mode 0755) when it is missing. Nothing else changes; the directory is not
# removed on uninstall (other packages may use it by then).
#
# Run it right after `:desktopApp:packageDeb`, before the .deb is uploaded or verified, so that the file that is
# checked is the file that ships. A .deb from a local `packageDeb` is not patched unless this script is run on it.
#
# Without <out.deb> the input would be overwritten, which needs --in-place. Running it again on a patched file changes
# nothing. It fails (and writes nothing) when the anchor line is not found exactly once in the postinst, when the
# compression of the package cannot be told, or when anything but the postinst differs after the repack.
set -euo pipefail

# The line of jpackage's postinst after which the line is inserted: the start of the "configure" branch, which runs
# the desktop integration commands. It must be there exactly once.
anchor='    configure)'
# The inserted line. Its presence marks a patched package.
insert='        [ -d /usr/share/desktop-directories ] || mkdir -m 0755 -p /usr/share/desktop-directories'

# Kept as given: the fakeroot session below starts this script again with the same arguments.
args=("$@")
in_place=false
if [ "${1:-}" = "--in-place" ]; then in_place=true; shift; fi
in="${1:?usage: harden-postinst.sh [--in-place] <in.deb> [<out.deb>]}"
out="${2:-}"
[ -f "$in" ] || { echo "::error::no such file: $in" >&2; exit 1; }
in="$(readlink -f "$in")"
if [ -z "$out" ]; then
  [ "$in_place" = true ] || { echo "::error::no output file given: pass <out.deb>, or --in-place to overwrite the input" >&2; exit 2; }
  out="$in"
else
  out="$(readlink -f "$out")"
  if [ "$out" = "$in" ] && [ "$in_place" != true ]; then
    echo "::error::the output is the input; pass --in-place to overwrite it" >&2
    exit 2
  fi
fi

# dpkg-deb -R and -b must see the same (faked) file ownership: run everything inside one fakeroot session.
if [ -z "${FAKEROOTKEY:-}" ]; then exec fakeroot -- "$0" "${args[@]}"; fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

die() { echo "::error::$1" >&2; exit 1; }

# The compression jpackage used: the members of the ar archive are control.tar.<ext> and data.tar.<ext>.
members="$(ar t "$in")"
ctrl_ext="$(sed -n 's/^control\.tar\.\{0,1\}\(.*\)$/\1/p' <<< "$members")"
data_ext="$(sed -n 's/^data\.tar\.\{0,1\}\(.*\)$/\1/p' <<< "$members")"
[ "$ctrl_ext" = "$data_ext" ] || die "control and data use different compression (control.tar.$ctrl_ext, data.tar.$data_ext)"
case "$data_ext" in
  xz) comp=xz ;;
  gz) comp=gzip ;;
  zst) comp=zstd ;;
  "") comp=none ;;
  *) die "unknown compression of the package: $data_ext" ;;
esac
echo "compression of $in: $comp"

dpkg-deb -R "$in" "$work/old"
postinst="$work/old/DEBIAN/postinst"
[ -f "$postinst" ] || die "the package has no DEBIAN/postinst"

if [ "$(grep -cFx -- "$insert" "$postinst")" -eq 1 ]; then
  echo "the postinst is already patched; nothing to do"
  if [ "$out" != "$in" ]; then cp -- "$in" "$out"; fi
  exit 0
fi
[ "$(grep -cFx -- "$insert" "$postinst")" -eq 0 ] || die "the postinst has the inserted line more than once"
n="$(grep -cFx -- "$anchor" "$postinst" || true)"
[ "$n" -eq 1 ] || die "the anchor line '$anchor' is in the postinst $n times, not exactly once: jpackage's template changed, update this script"

cp -a "$work/old" "$work/new"
awk -v anchor="$anchor" -v insert="$insert" '{ print } $0 == anchor { print insert }' "$postinst" > "$work/new/DEBIAN/postinst.tmp"
cat "$work/new/DEBIAN/postinst.tmp" > "$work/new/DEBIAN/postinst"   # keeps the file's mode
rm "$work/new/DEBIAN/postinst.tmp"
echo "---- postinst: changes ----"
diff -u "$postinst" "$work/new/DEBIAN/postinst" || true
echo "---------------------------"

dpkg-deb "-Z$comp" -b "$work/new" "$work/patched.deb" > /dev/null

# Nothing but the postinst may differ: the file list (mode, owner, size, path; not the date), and every control file.
listing() { dpkg-deb -c "$1" | awk '{ $4 = ""; $5 = ""; print }'; }
diff <(listing "$in") <(listing "$work/patched.deb") > /dev/null || die "the file list of the package differs after the repack"
dpkg-deb -R "$work/patched.deb" "$work/check"
if ! diff -r -q "$work/old/DEBIAN" "$work/check/DEBIAN" > "$work/control.diff"; then
  if [ "$(cat "$work/control.diff")" != "Files $work/old/DEBIAN/postinst and $work/check/DEBIAN/postinst differ" ]; then
    cat "$work/control.diff" >&2
    die "more than the postinst differs after the repack"
  fi
fi
[ "$(stat -c %a "$work/check/DEBIAN/postinst")" = "$(stat -c %a "$postinst")" ] || die "the mode of the postinst changed"

mv -- "$work/patched.deb" "$out.tmp.$$"
mv -- "$out.tmp.$$" "$out"
echo "patched: $out"
