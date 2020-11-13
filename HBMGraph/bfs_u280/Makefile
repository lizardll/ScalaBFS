SBT = sbt
CUR_DIR = $(shell pwd)
OBJDIR = $(CUR_DIR)/obj
PROJECTDIR = $(CUR_DIR)/project
TARGETDIR = $(CUR_DIR)/target

hdl: unzip top movev update_kernel

top: 
	$(SBT) -mem 51200 "runMain HBMGraph.Top"
	python add_init.py
	
movev:
	cp Top.v ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.srcs/sources_1/imports/bfs_u280/

creat_kernel:
	cd ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/;\
	echo "exit" >> bfs_project.tcl
	vivado -mode tcl -source bfs_project.tcl
	mv -f FinalBFS_32_ex ../
	cd ../../../../../HBMGraph/bfs_u280/

unzip:
	tar -vzxf ../../HBMGraph-proj.tar.gz -C ../..
update_kernel:
	rm -f ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/exports/FinalBFS_32.xo
	echo "open_project ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32_ex.xpr" > update_kernel.tcl
	echo "update_compile_order -fileset sources_1" >> update_kernel.tcl
	echo "source -notrace ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/imports/package_kernel.tcl" >> update_kernel.tcl
	echo "package_project ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32 mycompany.com kernel FinalBFS_32" >> update_kernel.tcl
	echo "package_xo  -xo_path ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/exports/FinalBFS_32.xo -kernel_name FinalBFS_32 -ip_directory ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/FinalBFS_32 -kernel_xml ../../HBMGraph-proj/workspace/FinalBFS_32/vivado_rtl_kernel/FinalBFS_32_ex/imports/kernel.xml" >> update_kernel.tcl
	echo "file mkdir ../../HBMGraph-proj/workspace/FinalBFS_32/src/vitis_rtl_kernel/FinalBFS_32" >> update_kernel.tcl
	echo "exit" >> update_kernel.tcl
	vivado -mode tcl -source update_kernel.tcl

movecpp:
	cp -f /space/graph_data/HBMGraph/hostcpp/* ../../HBMGraph-proj/workspace/FinalBFS_32/src/vitis_rtl_kernel/FinalBFS_32/

testall: testp1 testp2 testp3 testp4 testmem testreq testmaster testres move
	
testp1:
	$(SBT) "runMain HBMGraph.Testp1"

testp2:
	$(SBT) "runMain HBMGraph.Testp2"

testmem_write:
	$(SBT) "runMain HBMGraph.TestMem_write"

testmemory:
	$(SBT) "runMain HBMGraph.TestMemory"

testv:
	$(SBT) "runMain HBMGraph.Testv"

testw:
	$(SBT) "runMain HBMGraph.Testwrite_frontier_and_level"

testreq:
	$(SBT) "runMain HBMGraph.Testreq"

testmaster:
	$(SBT) "runMain HBMGraph.Testmaster"

testres:
	$(SBT) "runMain HBMGraph.Testres"

test:
	$(SBT) "runMain HBMGraph.bfsTester"

testbram:
	$(SBT) "runMain HBMGraph.Testbram"

testbram2:
	$(SBT) "runMain HBMGraph.Testbram2"

testfrontier:
	$(SBT) "runMain HBMGraph.Testfrontier"

move0:
	@if [ ! -d $(OBJDIR) ]; then mkdir -p $(OBJDIR); fi;
	mv *.v $(OBJDIR)/
	mv *.json $(OBJDIR)/
	mv *.fir $(OBJDIR)/

clean:
	rm -f *.v
	rm -f *.json
	rm -f *.fir
	rm -f .*.swo
	rm -f .*.swp
	rm -rf $(OBJDIR)
	rm -rf $(PROJECTDIR)
	rm -rf $(TARGETDIR)
	rm -f *.log
clear:
	rm -f *.json
	rm -f *.fir
	rm -f .*.swo
	rm -f .*.swp
	rm -f $(OBJDIR)/*.json
	rm -f $(OBJDIR)/*.fir
	rm -f $(OBJDIR)/.*.swo
	rm -f $(OBJDIR)/.*.swp
	




