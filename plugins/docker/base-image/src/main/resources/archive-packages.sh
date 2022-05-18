#!/usr/bin/env bash
set -e

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

function archive_yum_packages() {
  bash "$SCRIPT_DIR/print-installed-packages.sh"
  # TODO: invoke jfrog-cli to upload /var/cache/yum/*.rpm
}

function archive_apt_packages() {
  bash "$SCRIPT_DIR/print-installed-packages.sh"
  # TODO: invoke jfrog-cli to upload /var/cache/apt/archives/*.deb
}

if command -v yum &>/dev/null; then
  archive_yum_packages
elif command -v apt &>/dev/null; then
  archive_apt_packages
else
  echo "No supported package manager found"
  exit 1
fi
