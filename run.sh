#!/usr/bin/env bash
set -e

CMD_PATH="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
if [[ "$#" -lt 1 ]]; then
    echo "Need to give file to read from"
    exit 1
fi
pushd ${CMD_PATH} && mvn package && popd

java -jar ${CMD_PATH}/target/timesheet-reader.jar --debug -d 2 -A "Date" -D "rel. Duration" "$1"
