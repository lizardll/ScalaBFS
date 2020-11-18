#include <stdio.h>
#include <ctype.h>
#include <stdlib.h>
#include <string.h>
#include <iostream>
#include <queue>
#include <vector>
#include <map>
#include <algorithm>
#include <sys/time.h>
#include <cmath>
//kyle : 将乱序或有序的无向图转换为有序的有向图
using namespace std;
struct edge {
    unsigned int x , y;
};
bool cmp(struct edge a , struct edge b);
int main(int argc, char *argv[]) {
    if(argc != 2){
        cout << "args error" << endl;
        exit(1);
    }
    string graghfile;
    FILE * fp , * result;
    int i , j ,fscanfcount;
    struct edge temp;
    vector<struct edge> graph;
    graph.clear();
    graghfile = argv[1];
    fp = fopen((const char *)(graghfile).c_str(), "r");
    while(!feof(fp)) {
		// read a data tuple
		fscanfcount = fscanf(fp, "%u %u", &i, &j);
        if (fscanfcount == 2){
	    if(i == 2) i = 3;
	    if(j == 2) j = 3;
            temp.x = i;
            temp.y = j;
            graph.push_back(temp);
            if(i!=j)
            {
                temp.x = j;
                temp.y = i;
                graph.push_back(temp);
            }
        }
	}
    result = fopen((const char *)(graghfile.substr(0,graghfile.find_first_of('.'))+"_transfer_to_directed.txt").c_str(),"w");
    sort(graph.begin(),graph.end(),cmp);
    vector<struct edge>::iterator it;
    for(it = graph.begin();it != graph.end() ; it++){
        fprintf(result,"%u %u\n",it->x,it->y);
    }
    fclose(fp);
    fclose(result);
    return 0;
}
bool cmp(struct edge a , struct edge b){
    if(a.x != b.x){
        return a.x < b.x;
    }
    else return a.y < b.y;
}
