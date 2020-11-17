#include <stdio.h>
#include <ctype.h>
#include <stdlib.h>
#include <string>
#include <iostream>
#include <queue>
#include <algorithm>
#include <vector>
#include <map>
#include <cmath>

using namespace std;
typedef unsigned long long uint64;
typedef long long int64;
typedef unsigned int uint32;
uint32	cpuBFS(
			uint32 *graphData, uint32 *graphIndices,uint32 * level_array,uint32 * visited_map,uint32 root);
//unsigned int qc_addr,qn_addr,level_addr,data_depth,q_length;
//unsigned int C_addr,R_addr;
int node_num = 0;
int main(int argc, char *argv[]) {
	//args
	if(argc != 4){
		cout<<"please use correct argument!"<<endl;
		cout<<"Usage: ScalaBFS [filename with suffix] [the number of channels] [the number of PEs]"<<endl;
		cout<<"Example: ScalaBFS soc-livejournal.txt 32 64"<<endl;
	}
	int ch_num = atoi(argv[2]);
	uint32 pe_num = atoi(argv[3]);
		
	uint32 ali_num = pe_num / ch_num * 2;
	int divide_num = ch_num;
	//divide_num : channal 
	// Declare file pointers, and other variables
	FILE * fp;
//	FILE * fbin;
	FILE * flog;
	FILE * faddr;
	FILE * fdebug;
	uint32 zero = 0;
	uint32 one = 1;
	uint32 M, N, nonZeroCount;
	unsigned int i, j;
	unsigned int count = 0, level = 0;
	unsigned int root = 0;
	uint64 root_csr = 0;
	unsigned int data_depth = 0;
	unsigned int data_width = 0;
	unsigned int csr_r_addr = 0;
	unsigned int csr_c_addr = 0;
	unsigned int csc_r_addr = 0;
	unsigned int csc_c_addr = 0;
	unsigned int q_length = 0;
	unsigned int data_width_bits = 64;
	unsigned int level_addr = 0;

	string GraphFile = argv[1];
	string FileName = GraphFile.substr(0,GraphFile.find_first_of('.'));
	string BinFile = FileName+"_csr_csc.bin";
	string LogFile = FileName+"_csc.txt";
	string DebugFile = FileName+"debug.log";
	// Open graph file
	fp = fopen(GraphFile.c_str(), "r");
	if (fp == NULL) {
		cout << "Error: can't open graph file!" << endl;
		exit(1);
	}

	flog = fopen(LogFile.c_str(), "w+");
	if (flog == NULL) {
		cout << "Error: can't create log file!" << endl;
		exit(1);
	}	

	fdebug = fopen(DebugFile.c_str(), "w+");
	if (flog == NULL) {
		cout << "Error: can't create log file!" << endl;
		exit(1);
	}
	// Read and spare the first line to get M, N, and number of non zeros
	fscanf(fp, "%u %u", &M, &nonZeroCount);
    N = M;

	//////////////////////////////////////////////////////////////
	// Allocate Memory on CPU/Convey
	//////////////////////////////////////////////////////////////
	// Allocate memory for graph vectors on CPU
	//csr
	//////////////////////////////////////////////////////////////
	// Setup (read graph, copy to convey memory)
	//////////////////////////////////////////////////////////////
	map<int, vector<int>, less<int>> graph_csc;
	map<int, vector<int>, less<int>> graph_csr;
	cout << "Start setup .." << endl;
	int fscanfcount = 0;
	// read the graph data from file
	while(!feof(fp)) {
		// read a data tuple
		fscanfcount = fscanf(fp, "%u %u", &i, &j); 
		if(i>=node_num){
			node_num = i;
		}
		if(j>=node_num){
			node_num = j;
		}
		graph_csc[j].push_back(i);
		graph_csr[i].push_back(j);
	}
	node_num+=1;

	//csr
	uint32 *graphData    	= (uint32*) malloc(16 * nonZeroCount * sizeof(uint32));
	uint32 *graphIndices 	= (uint32*) malloc((node_num + 1) * sizeof(uint32));
	uint64 *graphInfo 			= (uint64*) malloc((node_num + 1) * sizeof(uint64));

	//csc
	uint32 *cscData    	= (uint32*) malloc(16 * nonZeroCount * sizeof(uint32));
	uint32 *cscIndices 	= (uint32*) malloc((node_num + 1) * sizeof(uint32));
	uint64 *cscInfo 			= (uint64*) malloc((node_num + 1) * sizeof(uint64));



	//csr
	graphIndices[0] = 0;
	map<int, vector<int>, less<int>>::iterator iter_csr;
	int count_csr = 0;
	int point_csr = 0;
	int pre_count_csr = 0;
	int max_edge_csr = 0;
	int count_temp = 0;
	for (iter_csr = graph_csr.begin(); iter_csr != graph_csr.end(); iter_csr++)
	{
		if (iter_csr->first > point_csr)
		{
			for (int p = point_csr; p <= iter_csr->first; p++)
			{
				graphIndices[p] = pre_count_csr;
			}
		}
		vector<int> b = iter_csr->second;
		if(b.size()>=max_edge_csr){
			max_edge_csr = b.size();
		}
		for (int i = 0; i < b.size(); i++)
		{
			graphData[count_csr] = b[i];
			fprintf(flog, "%d %d\n" ,iter_csr->first,b[i]);
			count_csr++;
		}
		// if(count_csr%2 == 1){
		// 	graphData[count_csr] = 0xffffffff;
		// 	count_csr++;
		// }

		if(count_csr%ali_num != 0){
			count_temp = count_csr%ali_num;
			for(int count_f = 0;count_f<(ali_num-count_temp);count_f++){
				graphData[count_csr] = 0xffffffff;
				count_csr++;
			}
		}

		graphIndices[iter_csr->first + 1] = count_csr;
		point_csr = iter_csr->first + 1;
		pre_count_csr = count_csr;
	}
    for (unsigned int k = ((--iter_csr)->first) + 1; k < node_num + 1; k++) {
        printf("csc not ending!\r");
		graphIndices[k] = count_csr;
	}
	// converting graphIndices to graphInfo
	for (int k = 0; k < node_num; k++) {
		// graphInfo = neigh_start_index (32 bit) | neighbours_count (32 bit) 
	uint32 index = (uint32) (graphIndices[k] / ali_num);	//	graphIndices/2
	uint32 size =   (uint32)((graphIndices[k+1] - graphIndices[k])/ali_num);
	graphInfo[k] = ((uint64)index << 32) | size;
	fprintf(fdebug,"index:%d size:%d i:%d graphIndicesk:%d graphIndicesk+1:%d \n",index,size,k,graphIndices[k],graphIndices[k+1]);
	}
	cout << "CSR Data generation done .." << endl;
	//csc
	cscIndices[0] = 0;
	map<int, vector<int>, less<int>>::iterator iter;
	int count_csc = 0;
	int point = 0;
	int pre_count = 0;
	int count0 = 0;
	int count1 = 0;
	int count00 = 0;
	int count11 = 0;
	int count_1_0 = 0;
	int count_1_1 = 0;
	int max_edge_csc = 0;
	for (iter = graph_csc.begin(); iter != graph_csc.end(); iter++)
	{
		if (iter->first > point)
		{
			for (int p = point; p <= iter->first; p++)
			{
				cscIndices[p] = pre_count;
			}
		}
		vector<int> b = iter->second;
		// if(b.size() > 255){
		// 	printf(">255");
		// }
		if(b.size()>=max_edge_csc){
			max_edge_csc = b.size();
		}
		if(iter->first !=1){
			if(iter->first%2 == 0){
				count00+= b.size();
			}else{
				count11+= b.size();
			}
			}
		
		for (int i = 0; i < b.size(); i++)
		{
			cscData[count_csc] = b[i];
			fprintf(flog, "%d %d\n" ,iter->first,b[i]);
			if(iter->first !=1){
			if(b[i]%2 == 0){
				count0++;
			}else{
				count1++;
			}
			}
			count_csc++;
		}
		// if(count_csc%2 == 1){
		// 	cscData[count_csc] = 0xffffffff;
		// 	count_csc++;
		// }
		if(count_csc%ali_num != 0){
			count_temp = count_csc%ali_num;
			for(int count_f = 0;count_f<(ali_num-count_temp);count_f++){
			cscData[count_csc] = 0xffffffff;
			count_csc++;
			}
		}
		cscIndices[iter->first + 1] = count_csc;
		point = iter->first + 1;
		pre_count = count_csc;
	}
    for (unsigned int k = ((--iter)->first) + 1; k < node_num + 1; k++) {
        printf("csc not ending!\r");
		cscIndices[k] = count_csc;
	}

	for (int k = 0; k < node_num; k++) {
		// graphInfo = neigh_start_index (32 bit) | neighbours_count (32 bit) 
	uint32 index = (uint32) (cscIndices[k] / ali_num);	//	graphIndices/2
	uint32 size =   (uint32)((cscIndices[k+1] - cscIndices[k])/ali_num);
	cscInfo[k] = ((uint64)index << 32) | size;
	fprintf(fdebug,"index:%d size:%d i:%d graphIndicesk:%d graphIndicesk+1:%d \n",index,size,k,cscIndices[k],cscIndices[k+1]);
	}

	cout << "CSC Data generation done .." << endl;

//divide
	vector <FILE *> fbin(divide_num);
	vector <vector <uint32>> csc_c(divide_num);
	vector <vector <uint64>> csc_r(divide_num);
	vector <vector <uint32>> csr_c(divide_num);
	vector <vector <uint64>> csr_r(divide_num);
	vector <uint32> csr_index_temp(divide_num,0);
	vector <uint32> csc_index_temp(divide_num,0);
	uint32 index_csr = 0;
	uint32 size_csr = 0;
	uint32 index_csc = 0;
	uint32 size_csc = 0;
	uint32 dest_num = 0;
	for(int r = 0;r<node_num;r++){
		dest_num = r%divide_num;
	//csr
		index_csr = (uint32)(graphInfo[r]>>32);
		size_csr = (uint32)graphInfo[r];
		//push c
		for(int csr_push_i = 0;csr_push_i < (size_csr*ali_num);csr_push_i++){
			csr_c[dest_num].push_back(graphData[ali_num * index_csr + csr_push_i]);
		}
		//push and count new r
		csr_r[dest_num].push_back(((uint64)csr_index_temp[dest_num] << 32) | size_csr);
		csr_index_temp[dest_num] += size_csr;
	//csc
		index_csc = (uint32)(cscInfo[r]>>32);
		size_csc = (uint32)cscInfo[r];
		//push c
		for(int csc_push_i = 0;csc_push_i < (size_csc*ali_num);csc_push_i++){
			csc_c[dest_num].push_back(cscData[ali_num * index_csc + csc_push_i]);
		}
		//push and count new r
		csc_r[dest_num].push_back(((uint64)csc_index_temp[dest_num] << 32) | size_csc);
		csc_index_temp[dest_num] += size_csc;
	}

	for(int addr_i = 0;addr_i<divide_num;addr_i++){
		if(csc_c[addr_i].size() > csc_c_addr){
			csc_c_addr = csc_c[addr_i].size();
			
		}
		if(csr_c[addr_i].size() > csr_c_addr){
			csr_c_addr = csr_c[addr_i].size();
		}
		if(csr_r[addr_i].size() > csr_r_addr){
			csr_r_addr = csr_r[addr_i].size();
		}
		if(csc_r[addr_i].size() > csc_r_addr){
			csc_r_addr = csc_r[addr_i].size();
		}
	}

	level_addr = csr_r_addr + csc_r_addr + csc_c_addr/ali_num + csr_c_addr/ali_num;
	csc_c_addr = csr_r_addr + csr_c_addr/ali_num + csc_r_addr;
	csc_r_addr = csr_r_addr + csr_c_addr/ali_num;
	csr_c_addr = csr_r_addr;
	csr_r_addr = 0;
	int *zero_num = {0};
	for(unsigned int file_i = 0;file_i<divide_num;file_i++){
		BinFile = FileName+"_pe_"+to_string(pe_num)+"_ch_"+to_string(ch_num)+"_"+to_string(file_i)+".bin";
		fbin[file_i] = fopen(BinFile.c_str(), "wb+");
		for(unsigned int j = 0; j<csr_c_addr; j++){
			if(j<csr_r[file_i].size()){
				fwrite(&csr_r[file_i][j],sizeof(uint64),1,fbin[file_i]);
				for(int count_r = 0;count_r<(ali_num/2-1);count_r++){
					fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				}
			}else{
				fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				for(int count_r = 0;count_r<(ali_num/2-1);count_r++){
					fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				}
			}
		}
		for(unsigned int i = 0; i<ali_num*(csc_r_addr-csr_c_addr); i++){
			if(i<csr_c[file_i].size()){
				fwrite(&csr_c[file_i][i],sizeof(uint32),1,fbin[file_i]);
			}else{
				fwrite(&zero_num,sizeof(uint32),1,fbin[file_i]);
			}
		}

		for(unsigned int j = 0; j<(csc_c_addr-csc_r_addr); j++){
			if(j<csc_r[file_i].size()){
				fwrite(&csc_r[file_i][j],sizeof(uint64),1,fbin[file_i]);
				for(int count_r = 0;count_r<(ali_num/2-1);count_r++){
					fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				}
			}else{
				fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				for(int count_r = 0;count_r<(ali_num/2-1);count_r++){
					fwrite(&zero_num,sizeof(uint64),1,fbin[file_i]);
				}
			}
		}
		for(unsigned int i = 0; i<ali_num*(level_addr-csc_c_addr); i++){
			if(i<csc_c[file_i].size()){
				fwrite(&csc_c[file_i][i],sizeof(uint32),1,fbin[file_i]);
			}else{
				fwrite(&zero_num,sizeof(uint32),1,fbin[file_i]);
			}
		}
		fclose(fbin[file_i]);
	}
	

	cout << "Dividation done .." << endl;

	//run
	uint32 *graphData_run    	= (uint32*) malloc(16 * nonZeroCount * sizeof(uint32));
	uint32 *graphIndices_run 	= (uint32*) malloc(4*(node_num + 1) * sizeof(uint32));
	unsigned int *level_array	= (unsigned int*) malloc(4*node_num*sizeof(unsigned int));
	unsigned int *visited_map = (unsigned int *)malloc(((4*node_num-1)/32 + 1)*sizeof(unsigned int));
	for(int i = 0 ; i < node_num ; i++){
		level_array[i] = 0;
	}
	for(int i = 0 ; i < (node_num-1)/32 + 1 ; i++){
		visited_map[i] = 0;
	}


		//csr
	graphIndices_run[0] = 0;
	map<int, vector<int>, less<int>>::iterator iter_run;
	int count_run = 0;
	int point_run = 0;
	int pre_count_run = 0;
	for (iter_run = graph_csr.begin(); iter_run != graph_csr.end(); iter_run++)
	{
		if (iter_run->first > point_run)
		{
			for (int p = point_run; p <= iter_run->first; p++)
			{
				graphIndices_run[p] = pre_count_run;
			}
		}
		vector<int> b = iter_run->second;

		for (int i = 0; i < b.size(); i++)
		{
			graphData_run[count_run] = b[i];
			count_run++;
		}
		graphIndices_run[iter_run->first + 1] = count_run;
		point_run = iter_run->first + 1;
		pre_count_run = count_run;
	}
    for (unsigned int k = ((--iter_run)->first) + 1; k < node_num + 1; k++) {
        printf("csc not ending!\r");
		graphIndices_run[k] = count_run;
	}



	cout << "Enter root node number(0 to N):" << endl;
	cin >> root;
	visited_map[root / 32] = 1 << (root % 32);
	cout << "cpuBFS running ..." << endl;	
	level = cpuBFS(graphData_run, graphIndices_run,level_array,visited_map,root);

	string AddrFile = FileName+"_addr_pe_"+to_string(pe_num)+"_ch_"+to_string(ch_num)+".log";
	faddr = fopen(AddrFile.c_str(), "w+");
	if (faddr == NULL) {
		cout << "Error: can't create faddr file!" << endl;
		exit(1);
	}
	cout << "successfully generated graph data, address log and cpuBFS's result!" << endl;
	fprintf(faddr, "    cl_uint csr_c_addr = %u;\n" ,csr_c_addr);
	fprintf(faddr, "    cl_uint csr_r_addr = %u;\n" ,csr_r_addr);
	fprintf(faddr, "    cl_uint level_addr = %u;\n" ,level_addr);
	fprintf(faddr, "    cl_uint node_num = %u;\n" ,node_num);
	fprintf(faddr, "    cl_uint csc_c_addr = %u;\n" ,csc_c_addr);
	fprintf(faddr, "    cl_uint csc_r_addr = %u;\n" ,csc_r_addr);
	fprintf(faddr, "max_edge_csr  = %u;\n" ,max_edge_csr);
	fprintf(faddr, "max_edge_csc = %u;\n" ,max_edge_csc);
	free(graphData);
	free(graphIndices);
	free(graphInfo);
	free(cscData);
	free(cscIndices);
	free(cscInfo);
	free(graphData_run);
	free(graphIndices_run);
	free(level_array);
	free(visited_map);

	fclose(fp);
	//fclose(fbin);
	fclose(flog);
	fclose(faddr);
	fclose(fdebug);
	return 0;
}

// Do BFS in CPU and return the number of traversed levels
inline uint32 cpuBFS(uint32 *graphData, uint32 *graphIndices, uint32 * level_array,uint32 * visited_map,uint32 root) {
	uint32 level = 1;
	int qc_count = 0;
	int qn_count = 0;
	// declare Next/Current queues
	queue <uint32> Current, Next;
	// Add root to next queue and it's level 1 
	Next.push(root);
	level_array[root] = level;

	// Traverse the graph
	while (!Next.empty()) {
		// pop next level into current level
		level ++;
		int i = 0;
		while (!Next.empty()) {
			Current.push(Next.front());
			i++;
			Next.pop();
		}
		qc_count = 0;
		qn_count = 0;
		// Traverse current level
		while (!Current.empty()) {
			uint32 current = Current.front();
			uint32 neigh_count = graphIndices[current + 1] - graphIndices[current];
			uint32 neigh_index = graphIndices[current];

			qc_count++;

			Current.pop();
			for (uint32 k = 0; k < neigh_count; k++,neigh_index++) {
 				// if neighbor is not visited, visit it and push it to next queue
				if ((visited_map[graphData[neigh_index]/32] & (1 << graphData[neigh_index])) == 0) {
					Next.push(graphData[neigh_index]);
					qn_count++;
					level_array[graphData[neigh_index]] = level;
					visited_map[graphData[neigh_index]/32] = visited_map[graphData[neigh_index]/32] | (1 << (graphData[neigh_index] % 32));
				}else{
				}
			}
			
		}

	}
	//kyle : result 打印最后的level和bitmap，作为比对仿真结果的基准
	FILE * result;
	result = fopen("result.txt", "w");
	for(int i = 0;i < node_num;i++){
		fprintf(result,"level[%u]%u\n",i,level_array[i]);
	}
	fclose(result);
	return level;
}
