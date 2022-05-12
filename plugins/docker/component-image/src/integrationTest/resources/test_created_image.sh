#!/bin/bash
echo ""
echo "Started verification script inside container started with the component image ..."

if ! [ "$(whoami)" == "root" ] ; then
   echo "Expected to be running as root but was not"!
   exit 100;
fi

if [ "$PWD" != "/home" ] ; then
   echo "Expected to be running in /home, cwd not set correctly ?"
   exit 100
fi

if ! ls -n build.gradle.kts | grep -q 1000 ; then
   echo "Expected /home/build.gradle.kts to exist and be owned by uid 1000"
   ls -nR .
   exit 100
fi

if ! ls -l $(uname -p)/build.gradle.kts | grep root ; then
   echo "Expected /home/$(uname -p)/build.gradle.kts to exist and be owned by root"
   echo "ls -Rl:"
   ls -Rl .
   echo "uname -p:"
   uname -p
   exit 100
fi

if ! [ "$1" == "foo" ] ; then
   echo "Expected arguments foo bar but got $1 $2"
   exit 100
fi
if ! [ "$2" == "bar" ] ; then
   echo "Expected arguments foo bar but got $1 $2"
   exit 100
fi



