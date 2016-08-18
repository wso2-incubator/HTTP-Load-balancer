#!/usr/bin/env bash

baseDir=$(dirname "$0")
service="$1"

concLevels="500 1000 2000 3000 4000 5000 6000 7000 8000 9000 10000 11000 12000"
perTestTime=80
testLoops=1000000
warmUpConc=200
warmUpLoop=50000

tmpDir="$baseDir/tmpData"
resultsDir="$baseDir/results"
timeStmp=$(date +%s)

declare -A MAP

function warmUp(){
echo "Warmup service.."
ab -t $perTestTime -k -s 30 -c $warmUpConc -n $warmUpLoop -r $service #> /dev/null
echo "Warmup service done"
}

function testConcLevel(){
local concLevel=$1

local resOut="$tmpDir/result-conc$concLevel-time$timeStmp-$(uuidgen)"
local percentOut="$tmpDir/percentile-conc$concLevel-time$timeStmp-$(uuidgen)"

echo "Testing Conc Level : $concLevel"

ab -t $perTestTime -k -c $concLevel -n $testLoops -e "$percentOut" -r $service > "$resOut"

local tps=$(cat "$resOut" | grep -Eo "Requests per second.*" | grep -Eo "[0-9]+" | head -1)

local meanLat=$(cat "$resOut" | grep -Eo "Time per request.*\(mean\)" | grep -Eo "[0-9]+(\.[0-9]+)?")

local percents=$(cat "$percentOut" | grep -Eo ",.*" | grep -Eo "[0-9]+(\.[0-9]+)?" | tr '\n' ',')
percents="$concLevel, $percents"

echo "At concurrency $concLevel"

MAP["$concLevel-tps"]=$tps
echo -e "\tThroughput $tps"

MAP["$concLevel-meanLat"]=$meanLat
echo -e "\tMean latency is $meanLat"

MAP["$concLevel-percents"]=$percents
echo -e "\tPercentiles are $percents"

echo "Testing Conc Level $concLevel is done"
}

function processResults(){
    local metric=$1
    local resultsFile=$2
    local conc=""

    rm -f "$resultsFile"

    local isPrintH=true
    for conc in $concLevels
    do
    	local header=""
    	if "$isPrintH"
     	then
        	header="Concurrency"
        fi
        local line="$conc"

        local tps=${MAP["$conc-$metric"]}
        line+=", $tps"

        if "$isPrintH"
        then
        	echo "$header" >> "$resultsFile"
        isPrintH=false
        fi
        echo "$line" >> "$resultsFile"
    done
    echo "" >> "$resultsFile"

    echo "==========================================="
    echo "            Results ($metric)              "
    echo "==========================================="
    cat "$resultsFile"
}

function processPercentiles(){
    local resultsFile=$1
    local vendorI=0
    local conc=""

    rm -f "$resultsFile"

    local header="Concurrency"
    for hVal in $(seq 0 100)
    do
        header+=", $hVal"
    done



    for conc in $concLevels
    do
    	local isPrintH=true
        local percents=${MAP["$conc-percents"]}
        if "$isPrintH"
        then
        	echo "$header" >> "$resultsFile"
            isPrintH=false
        fi
            echo "$percents" >> "$resultsFile"

            echo "" >> "$resultsFile"
    done

    echo "==========================================="
    echo "            Results (Percentiles)              "
    echo "==========================================="
    cat "$resultsFile"
}

function iterateConcLevels(){

warmUp # Warming up

if [ -d "$tmpDir" ]
then
	echo "$tmpDir exists."
else
	mkdir "$tmpDir"
fi

local concLevel=""
for concLevel in $concLevels
    do
        testConcLevel $concLevel
    done


if [ -d "$resultsDir" ]
then
	echo "$resultsDir exists."
else
	mkdir "$resultsDir"
fi

processResults "tps" "$resultsDir/tps.csv"
echo ""
processResults "meanLat" "$resultsDir/latency.csv"
echo ""
processPercentiles "$resultsDir/percentiles.csv"
echo ""

}


iterateConcLevels



