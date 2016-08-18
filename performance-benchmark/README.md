## Performance Test using High Performance Netty Back-End

In this performance test, five instances of simple service created using Netty framework were used.  Each instance is a fast backend (0s delay) with response of size 1KB.
See [netty backend] (services/Netty) for more details.  

Performance bench-marks were done between [open source Nginx load balancer] (nginx) and [GW-LB] (gw-lb) on [Ubuntu VM] (test-bed) in JVM v1.8.0_91 with default configuration.

**One Million (1,000,000) requests** were sent at different **concurrency levels (500 to 12,000)** to Netty backend, Nginx and GW-LB using apache bench via this [automated script] (excecute-tests.sh).

Benchmarks were conducted in Round-Robin algorithm mode with no persistence policies.

- See [Nginx config] (nginx/nginx.conf) for configuration details.
- See [GW-LB config] (gw-lb/gwLB.iflow) for configuration details.  

## Prerequisite
* **apache2-utils** - This performance tests are executed using ApacheBench. Therefore in order to run the tests, apache2-utils
should be installed in the machine.

Run all tests using the following command from [performance-benchmark](performance-benchmark)

```
./run.sh [load-balancer-endpoint]
```

`Example: ./run.sh http://localhost:8290/stocks`

## Throughput Test

Tests were done twice.  Average of 'Average throughput' for each concurrency level is calculated and plotted.  

First graph shows throughput comparison between Open Source Nginx and GW-LB.  Second graph shows throughput comparison with Netty backend.

![Throughput] (graphs/throughput_without_netty.png)

![ThroughputWithNetty] (graphs/throughput_with_netty.png)

## Latency Test

Tests were done twice.  Average of 'Mean Latency' for each concurrency level is calculated and plotted.

![MeanLatency] (graphs/mean_latency.png)

## Memory Test</b>

**Java Flight Recorder (JFR)** is enabled while starting LB server and recording is stopped after load test ends.  The obtained JFR recording has memory usage details.

This graph shows **Committed, Reserved and Used Heap** values.

#### Enabling JFR in WSO2-Carbon Server

```
cd CARBON_HOME/bin

Open carbon.sh in any text editor
```

Then add the following lines in between "-Dfile.encoding=UTF8 \" and "org.wso2.carbon.bootstrap.Bootstrap $*"

```
-XX:+UnlockCommercialFeatures \
-XX:+FlightRecorder \
-XX:FlightRecorderOptions=defaultrecording=true,disk=true,maxage=0,repository=./tmp,dumponexit=true,dumponexitpath=./ \
```




![UsedMemory] (graphs/memory_using_jfr.png)
 
## Reference  

- https://github.com/wso2/msf4j/tree/master/perf-benchmark

  Scripts available in the above mentioned repo were customized for this project.

- http://isuru-perera.blogspot.in/2015/02/java-flight-recorder-continuous-recordings.html

  Detailed post on enabling JFR in Carbon servers and setting up Cron job for the same. 

