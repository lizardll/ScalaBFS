#! /usr/bin/python  
fp = open("Top.v", "r")
findmodule = 0 
findmodule2 = 0
findmodule3 = 0
findinit = 0
find4 = 0
find5 = 0
find = 0
modulename = "module RRArbiter"   
modulename2= "module sub_crossbar"   
# modulename3= "module uram"  

init = "initial begin"   
cont = "\tlastGrant = 1'b0;"
f = fp.read()
a = f.split('\n')
i = 0
count = 0
for s in a:
    i = i + 1
    findmodule = s.find(modulename)
    findmodule2 = s.find(modulename2)
    # findmodule3 = s.find(modulename3)
    # find4 = s.find('reg [31:0] uram_douta;')
    # find5 = s.find('reg [31:0] uram_doutb;')
    if findmodule >= 0:
        print(s)
        find = find + 1
    if find > 0:
        findinit = s.find(init)
        if(findinit >= 0):
            a.insert(i, cont)
            print("write")
            find = 0
    # if findmodule2 >= 0:
    #     a[i - 1] = '(* keep_hierarchy = "yes" *) ' + a[i - 1]
    # if findmodule3 >= 0:
    #     a[i - 1] = '(* dont_touch = "true" *)  ' + a[i - 1]
    # if s.find("reg [31:0] uram_douta;") >= 0:
    #     a[i - 1] = '(* dont_touch = "true" *)  ' + a[i - 1]
    #     count = count + 1
    # if s.find("reg [31:0] uram_doutb;") >= 0:
    #     a[i - 1] = '(* dont_touch = "true" *)  ' + a[i - 1]
    #     count = count + 1
f = '\n'.join(a)
fp = open("Top.v", "w")
fp.write(f)
fp.close()
print(count)