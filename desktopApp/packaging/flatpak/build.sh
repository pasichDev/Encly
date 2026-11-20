#!/usr/bin/env bash
# Builds the Encly Flatpak bundle (desktopApp/build/flatpak/encly-<version>-<arch>.flatpak) on Linux.
#
#   desktopApp/packaging/flatpak/build.sh
#
# Needs: flatpak, flatpak-builder, and the freedesktop Platform and Sdk 25.08 (flatpak-builder installs them
# from Flathub with --install-deps-from). For the checks below: appstreamcli (package `appstream`) and
# desktop-file-validate (package `desktop-file-utils`). Environment:
#   ENCLY_FLATPAK_SKIP_GRADLE=1    the app image is already built (CI runs Gradle in its own step)
#   ENCLY_FLATPAK_REQUIRE_LINT=1   a missing validator is an error, not a skipped check (CI)
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../.." && pwd)"
app_id="io.github.pasichdev.Encly"
out="$root/desktopApp/build/flatpak"
image_parent="$root/desktopApp/build/compose/binaries/main/app"

version="$(sed -n 's/^VERSION_MAJOR=//p' "$root/version.properties" | tr -d '[:space:]').$(sed -n 's/^VERSION_MINOR=//p' "$root/version.properties" | tr -d '[:space:]').$(sed -n 's/^VERSION_PATCH=//p' "$root/version.properties" | tr -d '[:space:]')"
arch="$(flatpak --default-arch)"

# 1. The metainfo must describe the version being built.
if ! grep -q "<release version=\"$version\"" "$here/$app_id.metainfo.xml"; then
  echo "::error file=desktopApp/packaging/flatpak/$app_id.metainfo.xml::no <release version=\"$version\"> (version.properties) in the metainfo"
  exit 1
fi

# 2. Metadata checks, before the long build.
need() {
  if command -v "$1" > /dev/null; then return 0; fi
  if [ "${ENCLY_FLATPAK_REQUIRE_LINT:-0}" = 1 ]; then
    echo "::error::$1 is required"
    exit 1
  fi
  echo "warning: $1 not found, skipping its check" >&2
  return 1
}
if need appstreamcli; then appstreamcli validate --no-net "$here/$app_id.metainfo.xml"; fi
if need desktop-file-validate; then desktop-file-validate "$here/$app_id.desktop"; fi
if need xmllint; then xmllint --noout "$here/$app_id.mime.xml"; fi

# 3. The app image (jpackage app image with its own Java runtime).
if [ "${ENCLY_FLATPAK_SKIP_GRADLE:-0}" != 1 ]; then
  (cd "$root" && ./gradlew :desktopApp:createDistributable)
fi
mapfile -t images < <(find "$image_parent" -mindepth 1 -maxdepth 1 -type d)
if [ "${#images[@]}" -ne 1 ]; then
  echo "::error::expected exactly one app image in $image_parent, found ${#images[@]}"
  exit 1
fi
mapfile -t launchers < <(find "${images[0]}/bin" -maxdepth 1 -type f -perm -u+x)
if [ "${#launchers[@]}" -ne 1 ]; then
  echo "::error::expected exactly one launcher in ${images[0]}/bin, found ${#launchers[@]}"
  exit 1
fi

# 4. Stage: the image as it is, and the launcher the manifest installs as /app/bin/encly.
rm -rf "$out/app" "$out/encly"
mkdir -p "$out"
cp -a "${images[0]}" "$out/app"
printf '#!/bin/sh\nexec /app/encly/bin/%s "$@"\n' "$(basename "${launchers[0]}")" > "$out/encly"
chmod 755 "$out/encly"

# 5. Build and bundle. The bundle names the runtime repository, so installing it fetches the runtime.
flatpak-builder --user --force-clean --disable-rofiles-fuse \
  --install-deps-from=flathub \
  --state-dir="$out/state" --repo="$out/repo" "$out/build" "$here/$app_id.yml"
flatpak build-bundle --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo \
  "$out/repo" "$out/encly-$version-$arch.flatpak" "$app_id"
echo "Built $out/encly-$version-$arch.flatpak"
