#!/usr/bin/env bash
set -e

if [[ "$RUNNING_INSIDE_DOCKER" == "true" ]]; then
  echo "Running inside container to verify the IT built image"

  if [[ "$RUNNING_ROOT" == "true" ]]; then
    echo "Running as root"
    cat /mnt/docker-base-image.lock | grep 'name: "bash"' -A 2 | grep version:
    BASH_VERSION_LOCKFILE=$(cat /mnt/docker-base-image.lock | grep 'name: "bash"' -A 2 | grep version: | cut -d: -f2 | sed 's/"//g')
    BASH_VERSION_LOCKFILE=$(echo $BASH_VERSION_LOCKFILE)
    BASH_RELEASE_LOCKFILE=$(cat /mnt/docker-base-image.lock | grep 'name: "bash"' -A 2 | grep release: | cut -d: -f2 | sed 's/"//g')
    BASH_RELEASE_LOCKFILE=$(echo $BASH_RELEASE_LOCKFILE)
    if which yum; then
      BASH_VERSION=$(yum info bash | grep Name -A 3 | grep Version | cut -f2 -d: | tail -n 1)
      BASH_VERSION=$(echo $BASH_VERSION)
      BASH_RELEASE=$(yum info bash | grep Name -A 3 | grep Release | cut -f2 -d: | tail -n 1)
      BASH_RELEASE=$(echo $BASH_RELEASE)
    else
      BASH_VERSION=$(apt list --installed | grep bash | cut -d' ' -f2)
      BASH_RELEASE=""
    fi

    echo "Checking that bash installed match the lockfile"
    if [ $BASH_VERSION_LOCKFILE != $BASH_VERSION ]; then
      echo "Expected bash version '$BASH_VERSION_LOCKFILE' to be installed, but have version '$BASH_VERSION' instead"
      exit 2
    fi
    if [ -n "$BASH_RELEASE" ]; then
      if [ $BASH_RELEASE_LOCKFILE != $BASH_RELEASE ]; then
        echo "Expected bash RELEASE '$BASH_RELEASE_LOCKFILE' to be installed, but have version '$BASH_RELEASE' instead"
        exit 2
      fi
    fi
  else
    echo "Running as default user"
    if ! which patch; then
      echo "expected to have patch installed but it was not"
      exit 2
    fi
    if ! [ -f "/home/foobar/foo.txt" ]; then
      echo "Expected /home/foobar/foo.txt to exist but it did not"
      exit 2
    fi
    if ! grep "sample content" "/home/foobar/foo.txt"; then
      echo "/home/foobar/foo.txt exists, but does not have the expected content"
      exit 2
    fi
    # shellcheck disable=SC2010
    if ! ls -ln /home/foobar/foo.txt | grep "1234 1234"; then
      echo "Expected /home/foobar/foo to be owned by 1234 but it was not"
      ls -ln /home/foobar/
      exit 2
    fi
    if ! [ -f "/home/foobar/build.gradle.kts" ]; then
      echo "Expected /home/foobar/build.gradle.kts to exist but it did not"
      exit 2
    fi
    # shellcheck disable=SC2010
    if ! ls -ln /home/foobar/build.gradle.kts | grep "0 0"; then
      ls -ln /home/foobar/
      echo "Expected /home/foobar/build.gradle.kts to be owned by root but it was not"
      exit 2
    fi
    if [ -z "$MYVAR_PROJECT" ]; then
      echo "Expected MYVAR_PROJECT to be set but it was not"
      exit 2
    fi
    if ! whoami | grep foobar; then
      echo "Expected to be running as foobar but was running as $(whoami) instead"
      exit 2
    fi
    if ! grep /mnt/ephemeral /home/foobar/bar.txt; then
      echo "Expected to have the ephemeral mnt path in bar.txt but it was not there"
      exit 2
    fi
    if ! grep -i $(uname -m) /home/foobar/bar.txt; then
      echo "Expected to have the architecture in bar.txt but it was not there"
      exit 2
    fi
    if ! [ -f "/home/foobar/run.listOf.1" ]; then
      echo "Expected /home/foobar/run.listOf.1 to exist but it did not"
      exit 2
    fi
    if ! [ -f "/home/foobar/run.listOf.1" ]; then
      echo "Expected /home/foobar/run.listOf.1 to exist but it did not"
      exit 2
    fi
    if ! which patch; then
      echo "expected to have patch installed but it was not"
      exit 2
    fi
    if ! grep foobar /home/foobar/whoami; then
      echo "Expected to have foobar in /home/foobar/whoami"
      cat /home/foobar/whoami
      exit 2
    fi

  fi
else
  chmod 777 "$PWD"
  chmod 777 test_created_image.sh

  set -e
  IMAGE_ID=$(cat build/dockerBaseImageBuild/image-*.idfile | sed 's/^sha256://')
  echo "Verifying built image ID $IMAGE_ID"

  docker run --rm -e RUNNING_INSIDE_DOCKER=true -e RUNNING_NON_ROOT=true -v "$PWD:/mnt" --entrypoint /bin/bash "$IMAGE_ID" "/mnt/$(basename "$0")"
  docker run --rm -e RUNNING_INSIDE_DOCKER=true -e RUNNING_ROOT=true --user=0:0 -v "$PWD:/mnt" --entrypoint /bin/bash "$IMAGE_ID" "/mnt/$(basename "$0")"
fi
