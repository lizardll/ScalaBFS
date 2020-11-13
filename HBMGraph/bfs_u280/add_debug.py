#! /usr/bin/python  
fp = open("Top.v", "r") 
f = fp.read()
a = f.split('\n')
i = 0
list_not_need = ['Queue', 'crossbar','Arbiter','RRArbiter','bram_controller','pipeline']
module_not_need = 0
list_io_need = ['master']
module_need_io = 0
for s in a:
    if s.find('module') >= 0 :
        for cont in list_not_need :
            if s.find(cont) >= 0 :
                module_not_need = 1
        for cont2 in list_io_need :
            if s.find(cont2) >= 0 :
                module_need_io = 1
    if s.find('endmodule') >= 0 :
        module_not_need = 0
        module_need_io = 0
    if module_not_need == 0 :
        if (s.find('input') >= 0 or s.find('output') >=0) and module_need_io == 1 :
            a[i] = '  (*mark_debug = "true"*)' + s
        if s.find('reg') >= 0 or s.find('wire') >=0 :
            if s.find(' _') < 0 and s.find('Counter.scala') < 0 :
                a[i] = '  (*mark_debug = "true"*)' + s
    i = i + 1
f = '\n'.join(a)
fp = open("Top.v", "w")
fp.write(f)
fp.close()
