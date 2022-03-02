#!/usr/bin/env bash

function print_yum_packages() {
  regex="(\S+)\s*:\s(\S+)"
  yum info $(rpm -qa) 2>/dev/null | while read line; do
    if [[ $line =~ $regex ]]; then
      key="${BASH_REMATCH[1]}"
      val="${BASH_REMATCH[2]}"
      if [ "$key" == "Name" ]; then
        package=$val
      elif [ "$key" == "Version" ]; then
        version=$val
      elif [ "$key" == "Release" ]; then
        release=$val
      elif [ "$key" == "Architecture" ] || [ "$key" == "Arch" ]; then
        arch=$val
      fi
    elif [ -z "$line" ]; then
      echo "$package,$version,$release,$arch"
    fi
  done
}

function print_apt_packages() {
  regex="(\S+)\/\S+\s(\S+)\s(\S+)"
  apt list --installed 2>/dev/null | while read line; do
    if [[ $line =~ $regex ]]; then
      package="${BASH_REMATCH[1]}"
      version="${BASH_REMATCH[2]}"
      arch="${BASH_REMATCH[3]}"
      # apt packages don't have an independent release revision
      # so we return empty for that field
      echo "$package,$version,,$arch"
    fi
  done
}

if command -v yum &>/dev/null; then
  print_yum_packages
elif command -v apt &>/dev/null; then
  print_apt_packages
else
  echo "No supported package manager found"
  exit 1
fi
