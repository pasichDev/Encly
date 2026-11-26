#!/usr/bin/env bash
# Installs the Linux packages of the desktop app on THIS machine and checks what was installed, the way a user
# gets them: the .deb with apt, the Flatpak bundle for the user. The window is never opened.
#
#   desktopApp/packaging/verify-linux-packages.sh <dir with one .deb and one .flatpak>
#
# Meant for a clean CI runner (it uses sudo, installs tools with apt and removes the packages again); it is run by
# the release workflow (job `linux-verify`) and by the pull request CI (job `linux packages (verify)`).
#
# A hard failure (an `::error::` line, exit 1) is a check a correct package satisfies. A `::warning::` is a check
# whose expected place is a guess about jpackage's layout; it never fails the run. When a check fails the script
# prints what it found (the package's file list, the installed tree), so a wrong assumption can be fixed from the log.
set -euo pipefail

dir="${1:?usage: verify-linux-packages.sh <dir with the .deb and the .flatpak>}"
app_id="io.github.pasichdev.Encly"
pkg="encly"

# Files whose licence texts every installer must carry (the names are in desktopApp/packaging/resources/common/licenses).
license_files=(THIRD-PARTY-NOTICES.txt skiko-LICENSE.txt skiko-NOTICE.txt skia-LICENSE.txt pdfbox-LICENSE.txt
  jna-LICENSE.txt dbus-java-LICENSE.txt bouncycastle-LICENSE.txt)

files=""   # `dpkg -L`, set after the .deb is installed
loc=""     # the Flatpak's install location

fail() {
  echo "::error::$1"
  if [ -n "$files" ]; then
    echo "---- files of the package '$pkg' (dpkg -L) ----"
    echo "$files"
    echo "-----------------------------------------------"
  fi
  if [ -n "$loc" ] && [ -d "$loc/files" ]; then
    echo "---- the installed Flatpak ($loc/files), 3 levels deep, and its licences ----"
    (cd "$loc/files" && find . -maxdepth 3 | LC_ALL=C sort) || true
    find "$loc/files/share/licenses" 2> /dev/null | LC_ALL=C sort || true
    echo "-----------------------------------------------------------------------------"
  fi
  exit 1
}
warn() { echo "::warning::$1"; }
pick() { grep -E "$1" <<< "$files" | head -n1 || true; }   # first file of the package that matches
one() {                                                    # one(<dir> <glob>) -> the single file, or fail
  local found
  mapfile -t found < <(find "$1" -maxdepth 1 -type f -name "$2")
  if [ "${#found[@]}" -ne 1 ]; then
    echo "found in $1:"
    ls -l "$1" || true
    fail "expected exactly one $2 in $1, found ${#found[@]}"
  fi
  printf '%s\n' "${found[0]}"
}

deb="$(one "$dir" '*.deb')"
bundle="$(one "$dir" '*.flatpak')"

echo "::group::tools"
sudo apt-get update
sudo apt-get install -y flatpak appstream desktop-file-utils
echo "::endgroup::"

# ---- the .deb -------------------------------------------------------------------------------------------------
echo "::group::the .deb: $deb"
dpkg-deb --info "$deb"
dpkg-deb -f "$deb" Package | grep -qx "$pkg" || fail "the .deb is not the package '$pkg' ($(dpkg-deb -f "$deb" Package))"
sudo apt-get install -y "./$deb"
dpkg -s "$pkg" | grep -q '^Status: install ok installed' || fail "'$pkg' is not installed after apt install"
files="$(dpkg -L "$pkg")"

launcher="$(pick '^/opt/[^/]+/bin/[^/]+$')"
if [ -z "$launcher" ] || [ ! -x "$launcher" ]; then fail "no executable launcher in /opt/*/bin"; fi
echo "launcher: $launcher"

desktop="$(pick '\.desktop$')"
[ -n "$desktop" ] || fail "no .desktop file in the package"
echo "desktop entry: $desktop"
grep -q '^Exec=' "$desktop" || warn "the desktop entry has no Exec= line"
grep -q 'application/vnd.encly.backup' "$desktop" || warn "the desktop entry does not list application/vnd.encly.backup"
desktop-file-validate "$desktop" || warn "desktop-file-validate reports problems in the jpackage desktop entry"

icon="$(pick '\.png$')"
[ -n "$icon" ] || fail "no .png icon in the package"

# The bundled runtime. Compose's jlink step strips the runtime's own commands, so there is no bin/java to run:
# the release file and the JVM library show that the runtime is whole.
rel="$(pick '/lib/runtime/release$')"
[ -n "$rel" ] || fail "no bundled runtime (lib/runtime/release)"
grep -E '^MODULES=.*java\.desktop' "$rel" > /dev/null || { cat "$rel"; fail "the bundled runtime has no java.desktop module"; }
grep -qE '/lib/runtime/lib/server/libjvm\.so$' <<< "$files" || fail "the bundled runtime has no lib/server/libjvm.so"

# Licence and notice texts of the bundled parts. The place under /opt/encly follows jpackage's layout and is not
# assumed: the files are looked for by their folder name.
for f in "${license_files[@]}"; do
  grep -qE "/licenses/$f$" <<< "$files" || fail "$f is not in the package (looked for */licenses/$f)"
done
grep -qE '/lib/runtime/legal/java\.base/LICENSE$' <<< "$files" || fail "the runtime's legal/java.base/LICENSE is not in the package"
# Encly's own licence (jpackage --license-file): where it ends up is not confirmed, so this is a warning.
if ! grep -qE '/share/doc/.*copyright$' <<< "$files"; then
  warn "no copyright file under share/doc (Encly's own licence, from --license-file); see the file list below"
  echo "$files" | grep -i -E 'licen|copyright' || true
fi

sudo apt-get remove -y "$pkg"
if dpkg -s "$pkg" 2> /dev/null | grep -q '^Status: install ok installed'; then fail "'$pkg' is still installed after apt remove"; fi
files=""
echo "::endgroup::"

# ---- the Flatpak bundle ---------------------------------------------------------------------------------------
echo "::group::the Flatpak bundle: $bundle"
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install --user -y --noninteractive "$bundle"
flatpak info --user "$app_id"
loc="$(flatpak info --user --show-location "$app_id")"
echo "installed at $loc"

# The sandbox is the minimum: no network, no filesystem access, the Secret Service for the PIN's device key.
perms="$(flatpak info --user --show-permissions "$app_id")"
echo "$perms"
if grep -E '^shared=.*network' <<< "$perms"; then fail "the Flatpak asks for network access"; fi
if grep -E '^filesystems=' <<< "$perms"; then fail "the Flatpak asks for filesystem access"; fi
grep -q 'org.freedesktop.secrets' <<< "$perms" || fail "the Flatpak has no access to the Secret Service"

meta="$loc/files/share/metainfo/$app_id.metainfo.xml"
[ -f "$meta" ] || fail "no metainfo at $meta"
appstreamcli validate --no-net "$meta" || fail "appstreamcli validate failed on the installed metainfo"
[ -f "$loc/files/share/applications/$app_id.desktop" ] || fail "no desktop entry in the Flatpak"
desktop-file-validate "$loc/files/share/applications/$app_id.desktop" || fail "desktop-file-validate failed on the Flatpak's desktop entry"
[ -f "$loc/files/share/icons/hicolor/512x512/apps/$app_id.png" ] || fail "no icon in the Flatpak"
[ -x "$loc/files/bin/encly" ] || fail "no /app/bin/encly launcher in the Flatpak"

lic="$loc/files/share/licenses/$app_id"
for f in "${license_files[@]}" LICENSE openjdk/java.base/LICENSE; do
  [ -f "$lic/$f" ] || fail "$f is not in /app/share/licenses/$app_id"
done

flatpak uninstall --user -y --noninteractive "$app_id"
loc=""
echo "::endgroup::"

echo "Both packages install and carry what they should."
