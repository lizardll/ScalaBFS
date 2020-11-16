# ScalaBFS: A Scalable BFS Accelerator on FPGA-HBM Platform

## Organization

The code for ScalaBFS using Chisel language is located in src/ directory. Vitis project is located in ScalaBFS-proj/ directory after unpacked. Graph data processing files are provied in preprocess/ directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Software

[Vitis 2020.1](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/2020-1.html)

Need to install platform for U280  version 201920_3.

### Environment

To compiling chisel code, need to install:

- Java
- sbt
- Scala

For host part of ScalaBFS, need to install:

- OpenCL

## Usage

### cloning the repository

```
$ git clone https://github.com/lizardll/ScalaBFS.git
```

### build the project

```
$ make
```

It includes decompressing the ScalaBFS-proj folder, compiling chisel code and updating  RTL kernel in Vitis project.

### program the project

- Open Vitis

- Select workspace in

  ```
  ScalaBFS-proj/workspace
  ```

- Select graph data

