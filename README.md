# ScalaBFS: A Scalable BFS Accelerator on FPGA-HBM Platform

## Organization

The code for ScalaBFS using Chisel language is located in src/ directory. Vitis project is located in ScalaBFS-proj/ directory after unpacked. Graph data processing files are provied in preprocess/ directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Operation System

Ubuntu 18.04 LTS

### Software

[Vitis 2020.1](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/2020-1.html)

[U280 Package File on Vitis 2020.1](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html#gettingStarted)

### Environment

To compile chisel code, you need to install:

- Java 1.0.8
- sbt 1.4.2
- Scala 2.12.8


## Build 

```
$ git clone https://github.com/lizardll/ScalaBFS.git
$ make
```

## Preprocess

## Deploy and play

- Open Vitis

- Select workspace:

  ```
  ScalaBFS-proj/workspace
  ```

- Select graph data

