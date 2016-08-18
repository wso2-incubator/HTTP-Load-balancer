# Test Bed
These are the configurations used during bench-marking.

#### VM Details

- `Guest OS     :` Ubuntu 64-bit 16.04 VM
- `RAM          :` 8 GB
- `CPU cores    :` 4
- `JVM Version  :` 1.8.0_91
- `Java Runtime :` Java(TM) SE Runtime Environment (build 1.8.0_91-b14)
- `Java HotSpot :` Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)

#### Host Machine Details

- `Host OS     :` OS X EI Captain Version 10.11.5 (15F34) MacBook Pro (Mid 2015)
- `Hypervisor  :` VMware Fusion Professional Version 8.1.1 (3771013)
- `Processor   :` 2.5 GHz Core i7
- `Memory      :` 16 GB 1600 MHz DDR3

## Configuration Changes in Ubuntu

- Maximum number of connections allowed has to be changed in this file.
  ```
  /etc/sysctl.conf 
  ```
- Increase open files limit in this file.  
  ```
  /etc/security/limits.conf
  ```
  
This directory contains those configuration files.
