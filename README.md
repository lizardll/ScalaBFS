# ScalaBFS: A Scalable BFS Accelerator on FPGA-HBM Platform

ScalaBFS is an BFS accelerator built on top of an FPGA configured with HBM (i.e., FPGA-HBM platform) which can scale its performance according to the available memory channels (on a single card). It utlizes multiple processing elements to sufficiently exploit the high bandwidth of HBM to improve efficiency. We implement the prototype system of ScalaBFS on Xilinx Alveo U280 FPGA card (real hardware).

## Organization

The code for ScalaBFS using Chisel language is located in src/ directory. Vitis project is located in ScalaBFS-proj/ directory after unpacked. Graph data processing files are provided in preprocess/ directory.

## Prerequisites

### Hardware

This project works on [Xilinx U280 Data Center Accelerator card](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html).

### Operation System

Ubuntu 18.04 LTS

### Software

[Vitis 2019.2](https://www.xilinx.com/support/download/index.html/content/xilinx/en/downloadNav/vitis/2019-2.html)

[U280 Package File on Vitis 2019.2](https://www.xilinx.com/products/boards-and-kits/alveo/u280.html#gettingStarted)

Notice:

1. After the installation of xdma and update the shell on alveo card manually(under normal circumstances , the command is shown in the process of the installtion of xdma. If not , you can use command "/opt/xilinx/xrt/bin/xbmgmt flash --update"), you should cold reboot your machine. The cold reboot means that you should shutdown your machine , unplug the power , wating for several minutes , plug the power and boot up your machine.You can use command 

```
/opt/xilinx/xrt/bin/xbmgmt flash --scan
/opt/xilinx/xrt/bin/xbutil validate
```

to make sure that the runtime enviroment and the alveo card is ready.

2. Don't forget to add the xrt and Vitis to your PATH. Typically you can 

```
source /opt/xilinx/xrt/setup.sh
source /tools/Xilinx/Vitis/2019.2/settings64.sh
```

You can also add this two commands to your .bashrc file.If in the process of making ScalaBFS you fail and see "make: vivado: Command not found", you very likely ignored this step.

3. If you meet "PYOPENCL INSTALL FAILED" in the installtion of xrt , refer to [AR# 73055](https://www.xilinx.com/support/answers/73055.html)

4. If you meet "XRT Requires opencl header" when you open Vitis , refer to [Vitis prompt “XRT Requires opencl header"](https://forums.xilinx.com/t5/Vitis-Acceleration-SDAccel-SDSoC/Vitis-prompt-XRT-Requires-opencl-header-quot/td-p/1087072)
### Environment

To compile chisel code, you need to install:

- Java 1.0.8

```
sudo apt install openjdk-8-jre-headless
sudo apt-get install java-wrappers    
sudo apt-get install default-jdk
```

- sbt 1.4.2

```
echo "deb https://dl.bintray.com/sbt/debian /" | \
sudo tee -a /etc/apt/sources.list.d/sbt.list
sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 \
--recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
sudo apt-get update
sudo apt-get install sbt
```

- Scala 2.11.12

```
sudo apt install scala
```

## Clone and Build 

```
$ git clone https://github.com/lizardll/ScalaBFS.git
$ make
```

## Quick Start Guide

### Preprocess

Before deploying and running ScalaBFS, we need to make sure that you have specific graph data with divided csc-csr format that ScalaBFS required.  For complete graph data preprocess guide, see [Data Preprocess.](https://github.com/lizardll/ScalaBFS/tree/master/data_preprocess)

We start with a small directed graph named Wiki-Vote for example. First we should make for directed or undirected graph for propose. Then we generate divided graph data with 32 channels and 64 PEs for ScalaBFS.

```bash
cd data_preprocess
make all
./GraphToScalaBFS Wiki-Vote.txt 32 64
```



### Deploy and play

#### Open Vitis & Select workspace:

  ```
  ScalaBFS-proj/workspace
  ```

#### Choose graph data (modify host_example.cpp in vitis)

For the preprocessed wiki-vote graph data mentioned before, we should first modify the input file name at line 121:

  ```
  string bfs_filename = "YOUR_DIR_HERE/ScalaBFS/data_preprocess/Wiki-Vote_pe_64_ch_";
  ```

Then we have to modify the following line 122-127 according to data_preprocess/Wiki-Vote_addr_pe_64_ch_32.log:

  ```
    cl_uint csr_c_addr = 260;
    cl_uint csr_r_addr = 0;
    cl_uint level_addr = 2958;
    cl_uint node_num = 8298;
    cl_uint csc_c_addr = 1780;
    cl_uint csc_r_addr = 1520;
  ```
  
After that, it's time to build the whole project in vitis. Select the "Hardware" target in the left down corner, and press the hammer button to build it! Genarally it will take 10~15 hours.

The results will be like this:


## Experiment results


<table>
<thead>
  <tr>
    <th></th>
    <th colspan="2">Gunrock on V100</th>
    <th colspan="2">ScalaBFS on U280</th>
  </tr>
</thead>
<tbody>
  <tr>
    <td>Datasets</td>
    <td>Throughput<br>(GTEPS)</td>
    <td>Power eff.<br>(GTEPS/watt)</td>
    <td>Throughput<br>(GTEPS)</td>
    <td>Power eff.<br>(GTEPS/watt)</td>
  </tr>
  <tr>
    <td>PK</td>
    <td>14.9</td>
    <td>0.050</td>
    <td>16.2</td>
    <td>0.506</td>
  </tr>
  <tr>
    <td>LJ</td>
    <td>18.5</td>
    <td>0.062</td>
    <td>11.2</td>
    <td>0.350</td>
  </tr>
  <tr>
    <td>OR</td>
    <td>150.6</td>
    <td>0.502</td>
    <td>19.1</td>
    <td>0.597</td>
  </tr>
  <tr>
    <td>HO</td>
    <td>73</td>
    <td>0.243</td>
    <td>16.4</td>
    <td>0.513</td>
  </tr>
</tbody>
</table>
