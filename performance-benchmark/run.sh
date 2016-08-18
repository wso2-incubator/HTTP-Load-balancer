#!/usr/bin/env bash

baseDir=$(dirname "$0")

#For later implementation, if necessary
function cleanup() {
echo "Inside Clean up.."
}

cleanup
echo $baseDir
$baseDir/excecute-tests.sh "$1"


if [ ! "$?" = 0 ]
then
    echo "Test were not completed successfully"
    echo "Cleaning up.."
    cleanup
fi
