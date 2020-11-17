# ScalaBFS Graph Data Preprocess Usage



### Generate Divided Graph Data with Scalable Channels and PEs

#### 1) Process Directed Graph

i) Download original graph data which have correct data format

Format: The fist line of graph file contains total vertices and edges of the graph. Then comes graph edges with adjacency list format.

| M[Vertices]    | N[Edges]      |
| -------------- | ------------- |
| a1[First node] | b1[Tail node] |
| a2             | b2            |
| ...            | ...           |
| an             | bn            |

ii) Generate Divided Graph Data with Scalable Channels and PEs

Usage: 

```bash
[executable program] [filename with suffix] [the number of channels] [the number of PEs]
```

Example:

```bash
cd data_preprocess
make directed
./GraphToScalaBFS soc-livejournal.txt 32 64
```

#### 2) Process Undirected Graph

i) Download original graph data which have correct data format

Format: The fist line of graph file contains total vertices and edges of the graph. Then comes graph edges with adjacency list format.

| M[Vertices]    | N[Edges]      |
| -------------- | ------------- |
| a1[First node] | b1[Tail node] |
| a2             | b2            |
| ...            | ...           |
| an             | bn            |

ii) Convert Undirected Graph to Directed Graph

Usage:

```bash
[executable program] [filename without suffix]
```

Example:

```bash
cd data_preprocess
make undirected
./transfer soc-livejournal
```

iii) Generate Divided Graph Data with Scalable Channels and PEs

```bash
./GraphToScalaBFS soc-livejournal.txt 32 64
```

### Well-tested Graph Data Set

| Graphs                  | Vertices(M) | Edges(M) | Avg Degree | Directed | Download Link |
| ----------------------- | ----------- | -------- | ---------- | -------- | ------------- |
| soc\-Pokec \(PK\)       | 1\.63       | 30\.62   | 18\.75     | Y        |               |
| soc\-LiveJournal \(LJ\) | 4\.85       | 68\.99   | 14\.23     | Y        |               |
| com\-Orkut \(OR\)       | 3\.07       | 234\.37  | 76\.28     | N        |               |
| hollywood\-2009 \(HO\)  | 1\.14       | 113\.89  | 99\.91     | N        |               |
| RMAT18\-8               | 0\.26       | 2\.05    | 7\.81      | N        |               |
| RMAT18\-16              | 0\.26       | 4\.03    | 15\.39     | N        |               |
| RMAT18\-32              | 0\.26       | 7\.88    | 30\.06     | N        |               |
| RMAT18\-64              | 0\.26       | 15\.22   | 58\.07     | N        |               |
| RMAT22\-16              | 4\.19       | 65\.97   | 15\.73     | N        |               |
| RMAT22\-32              | 4\.19       | 130\.49  | 31\.11     | N        |               |
| RMAT22\-64              | 4\.19       | 256\.62  | 61\.18     | N        |               |
| RMAT23\-16              | 8\.39       | 132\.38  | 15\.78     | N        |               |
| RMAT23\-32              | 8\.39       | 262\.33  | 31\.27     | N        |               |
| RMAT23\-64              | 8\.39       | 517\.34  | 61\.67     | N        |               |

