#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

function archive_yum_packages() {
  cd /var/cache/yum/
  bash "$SCRIPT_DIR/print-installed-packages.sh"
  # TODO: invoke jfrog-cli to upload /var/cache/yum/*.rpm

}

function archive_apt_packages() {
  bash "$SCRIPT_DIR/print-installed-packages.sh"

  # We need dpkg-scanpackages
  apt-get install -y dpkg-dev >&2

  # Make sure all installed packages are downloaded
  dpkg -l | grep "^ii"| awk ' {print $2} ' | xargs apt-get -y install --reinstall --download-only >&2

  cd /var/cache/apt/archives/

  # Rename some files that have url encoded `:` in their name as jfrog cli would otherwise double encode them
  for name in $(find . -name '*%*') ; do  mv "$name" "$(echo $name | sed s/%3a/:/g)" ; done

  dpkg-scanpackages . | gzip > Packages.gz

  # shellcheck disable=SC2086
  CI=TRUE /mnt/jfrog-cli rt upload \
        $JFROG_CLI_ARGS \
        "*.*" deb/ >&2

  echo "Packages.gz,$(sha256sum Packages.gz | cut -f1 -d' '),,$(uname -p)"
}

if command -v yum &>/dev/null; then
  archive_yum_packages
elif command -v apt &>/dev/null; then
  archive_apt_packages
else
  echo "No supported package manager found"
  exit 1
fi
