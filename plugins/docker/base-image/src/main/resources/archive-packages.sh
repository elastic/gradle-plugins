#!/usr/bin/env -e bash

function archive_yum_packages() {
  mkdir -p /var/rpms
  cd /var/rpms

  echo "Reading yum packages" >&2
  regex="(\S+)\.(\S+)\s*(\S+)-(\S+)"
  DOWNLOAD="/tmp/packages"
  yum list installed 2>/dev/null | xargs -n3 | while read line; do
      if [[ $line =~ $regex ]]; then
        package="${BASH_REMATCH[1]}"
        arch="${BASH_REMATCH[2]}"
        version="${BASH_REMATCH[3]}"
        release="${BASH_REMATCH[4]}"
        if echo "$version" | grep -q : ; then
           # We need to remove the "epoch" from the version number as it's not suppoerted in `yum install` ...
           version=$(echo "$version" | cut -d: -f2)
        fi
        echo "$package,$version,$release,$arch"
        echo "${package}-${version}-${release}" >> $DOWNLOAD
      fi
  done
  yum reinstall --downloadonly "--downloaddir=${PWD}"  $(< $DOWNLOAD) >&2
  rm $DOWNLOAD

  yum install -y createrepo >&2
  createrepo . >&2

  tar -cf repodata.tar repodata
  rm -Rf repodata

  REPODATA_VERSION=$(sha256sum repodata.tar | cut -f1 -d' ')
  REPODATA_ARCH=$(uname -p)

  mv repodata.tar "__META__repodata-${REPODATA_VERSION}-meta.${REPODATA_ARCH}.tar"

  # shellcheck disable=SC2086
  CI=TRUE /mnt/jfrog-cli rt upload \
      $JFROG_CLI_ARGS \
      "*.*" . >&2

  echo "__META__repodata,$REPODATA_VERSION,meta,$REPODATA_ARCH"
}

function archive_apt_packages() {
  # Make sure all installed packages are downloaded
  dpkg -l | grep "^ii" | awk ' {print $2} ' | xargs apt-get -y install --reinstall --download-only >&2

  # Move installed packages out of cache directory because apt-get install (below) clears the cache
  mkdir /tmp/packages
  cd /tmp/packages
  mv /var/cache/apt/archives/*.deb .

  # Rename some files that have url encoded `:` in their name as jfrog cli would otherwise double encode them
  for name in $(find . -name '*%*'); do mv "$name" "$(echo $name | sed s/%3a/./g)"; done

  # We have to rename the packages to match how these will be called in the Gradle configuration so the metadata that we
  # generate below will still work.
  regex="(\S+)\/\S+\s(\S+)\s(\S+)"
  apt list --installed 2>/dev/null | while read line; do
    if [[ $line =~ $regex ]]; then
      package="${BASH_REMATCH[1]}"
      version="${BASH_REMATCH[2]}"
      arch="${BASH_REMATCH[3]}"
      echo "$package,$version,,$arch"
      version="$(echo $version | sed s/:/./g)"
      mv "${package}_${version}_${arch}.deb" "${package}-${version}-${arch}.deb"
    fi
  done

  # We need dpkg-scanpackages
  apt-get install -y dpkg-dev >&2
  dpkg-scanpackages . | gzip >Packages.gz
  PACKAGES_VERSION=$(sha256sum Packages.gz | cut -f1 -d' ')
  PACKAGES_ARCH=$(uname -p)

  mv Packages.gz "__META__Packages-${PACKAGES_VERSION}-${PACKAGES_ARCH}.gz"

  # shellcheck disable=SC2086
  CI=TRUE /mnt/jfrog-cli rt upload \
    $JFROG_CLI_ARGS \
    "*.*" . >&2

  echo "__META__Packages,$PACKAGES_VERSION,,$PACKAGES_ARCH"
}

archive_apk_packages() {
  # Ensure all installed packages are downloaded
  rm -Rf /var/cache/apk/
  apk update >&2
  PACKAGES=$(apk info | grep -v chainguard-baselayout)
  echo "Installed packages: $PACKAGES" >&2
  URLS=$(apk fetch --url --simulate $PACKAGES)

  # Initialize an empty string to hold the final package=version list
  package_version_list=""

  # Packages in the base image don't upgrade with apk upgrade because they are locked in /etc/apk/world so we need to
  # do it explicitly. URLS will point to the last version in the registry.
  for url in $URLS; do
    # Extract the package name and version from the URL
    package_version_list="$package_version_list $(echo $url | sed 's/.*\/\(.*\)-\(.*-[^-]*\).apk/\1=\2/')"
  done
  apk add -q $package_version_list

  if apk info --installed curl > /dev/null ; then
    KEEP_CURL='yes'
  else
    echo "Adding curl so we can use it to download packages" >&2
    apk add -q curl >&2
    KEEP_CURL='no'
  fi


  # Create the target directory if it does not exist
  PACKAGES_ARCH=$(uname -m)
  mkdir -p /var/cache/apk/archives/
  mkdir -p "/var/cache/apk/archives/$PACKAGES_ARCH"

  # Iterate through each URL
  for URL in $URLS; do
      FILENAME=$(basename "$URL")
      echo "Downloading: $URL" >&2
      curl --silent -o "/var/cache/apk/archives/$PACKAGES_ARCH/$FILENAME" "$URL" >&2
  done
  if [ "$KEEP_CURL" == 'no' ] ; then
      echo "Removing curl so it's not part of the image" >&2
      apk del -q curl >&2
  fi

  echo "All files have been downloaded to /var/cache/apk/archives/$PACKAGES_ARCH" >&2

  cd "/var/cache/apk/archives/$PACKAGES_ARCH"

  FILE="/etc/apk/world"
  if [[ ! -f "$FILE" ]]; then
    echo "File $FILE not found!" >&2
    exit 1
  fi
  for line in $PACKAGES; do
    package="$line"
    version=$(apk info "$package" --installed --description | awk 'NR==1{print $1}' | sed "s/^$package-//")
    echo "Recording: $package=$version in the lockfile" >&2
    echo "$package,$version,,$arch"
  done < "$FILE"

  apk index --allow-untrusted -o Packages.gz *.apk >&2
  PACKAGES_VERSION=$(sha256sum Packages.gz | cut -f1 -d' ')
  echo "__META__Packages,$PACKAGES_VERSION,,$PACKAGES_ARCH"

  mv Packages.gz "__META__Packages-${PACKAGES_VERSION}.gz"

  cd "/var/cache/apk/archives/"
  # Upload the packages using jfrog-cli
  # shellcheck disable=SC2086
  CI=TRUE /mnt/jfrog-cli rt upload \
    $JFROG_CLI_ARGS \
    "*.*" . >&2
}


if command -v yum &>/dev/null; then
  archive_yum_packages
elif command -v apt &>/dev/null; then
  archive_apt_packages
elif command -v apk &>/dev/null; then
  archive_apk_packages
else
  echo "No supported package manager found"
  exit 1
fi
