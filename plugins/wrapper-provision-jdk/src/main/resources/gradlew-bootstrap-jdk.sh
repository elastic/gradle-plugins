[ $(uname -m) = "x86_64" ] && JDK_ARCH="x64" || JDK_ARCH="aarch64"
[ "$darwin" = true ] && JDK_OS="mac" || JDK_OS="linux"
JDK_VERSION="%{JDK_VERSION}%"
JDK_DOWNLOAD_URL="%{JDK_DOWNLOAD_URL}%"
JDK_CACHE_DIR="%{JDK_CACHE_DIR}%"
JDK_DOWNLOAD_FILE="$JDK_CACHE_DIR/jdk-$JDK_VERSION.tar.gz"

if ! which curl > /dev/null ; then
   echo "Provisioning the JDK in the wrapper requires curl to be available in the path, but it was not."
   exit 99
fi


if [ $JDK_OS = "mac" ]; then
  if [ $JDK_ARCH = "aarch64" ]; then
     # Adoptium doesn't have it yet
    JDK_DOWNLOAD_URL="%{JDK_DOWNLOAD_URL_M1_MAC}%"
  fi
fi

if [ -z "${JAVA_HOME_OVERRIDE}" ]; then
  JAVA_HOME="${JDK_CACHE_DIR}/jdk-${JDK_VERSION}"
  # make sure java home exists and it's not an empty dir
  if ! [ -d "$JAVA_HOME" ] || [ -z "$(ls -A $JAVA_HOME)" ]; then
    if ! [ -d "$JAVA_HOME" ]; then
      mkdir -p "${JAVA_HOME}" || die "Error while creating local cache directory: ${JAVA_HOME}"
    fi
    echo "Downloading JDK from $JDK_DOWNLOAD_URL"
    curl --silent -L "${JDK_DOWNLOAD_URL}" --output $JDK_DOWNLOAD_FILE
    if [ $JDK_OS = "mac" ]; then
      if [ $JDK_ARCH = "x64" ]; then
        if ! echo "%{CHECKSUM_DARWIN_X86_64}%  $JDK_DOWNLOAD_FILE" | shasum -c -a 256; then
          echo "Checksum verification of the downloaded JDK failed"
          exit 1
        fi
      else
        if ! echo "%{CHECKSUM_DARWIN_AARCH64}%  $JDK_DOWNLOAD_FILE" | shasum -c -a 256; then
          echo "Checksum verification of the downloaded JDK failed"
          exit 1
        fi
      fi
    elif [ $JDK_OS = "linux" ]; then
      if [ $JDK_ARCH = "x64" ]; then
        if ! echo "%{CHECKSUM_LINUX_X86_64}%  $JDK_DOWNLOAD_FILE" | sha256sum -c; then
          echo "Checksum verification of the downloaded JDK failed"
          exit 1
        fi
      elif [ $JDK_ARCH = "aarch64" ]; then
        if ! echo "%{CHECKSUM_LINUX_AARCH64}%  $JDK_DOWNLOAD_FILE" | sha256sum -c; then
          echo "Checksum verification of the downloaded JDK failed"
          exit 1
        fi
      else
        echo "Boostrapping JDK on Linux $JDK_ARCH is not yet supported, set JAVA_HOME_OVERRIDE to a valid JDK"
        exit 1
      fi
    else
      echo "Bootstrapping a JDK on $JDK_OS is not yet supported, set JAVA_HOME_OVERRIDE to a valid JDK"
      exit 1
    fi
    # extract and deal with different naming conventions on OSX
    if [ $JDK_OS = "mac" ]; then
      if [ $JDK_ARCH = "x64" ]; then
        tar -xzf $JDK_DOWNLOAD_FILE --strip-components=3 -C "${JAVA_HOME}/"
      else
        tar -xzf $JDK_DOWNLOAD_FILE --strip-components=1 -C "${JAVA_HOME}/"
      fi
    else
      tar -xzf $JDK_DOWNLOAD_FILE --strip-components=1 -C "${JAVA_HOME}/"
    fi
    rm -f $JDK_DOWNLOAD_FILE
    chmod -R u+w,g+w "${JAVA_HOME}"
    echo "Installed JDK from ${JDK_DOWNLOAD_URL} into ${JAVA_HOME}"
  fi
else
  JAVA_HOME="${JAVA_HOME_OVERRIDE}"
fi
