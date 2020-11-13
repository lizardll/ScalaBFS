# 头文件
    package 统一改成HBMGraph

# Class和Bundle写法
    tab缩进用4个空格
	端口和模块都必须带上注释
	不需要重复使用的端口定义在模块内部
	尽量使用decoupled信号来控制数据流
	端口数量可能大于1的情况下必须用参数生成
	queue接在收的一方

# 变量命名
	采用下划线命名

# 流水线和AXI间的接口
	使用decoupled接口来给出地址，接收数据

# Git使用
	checkout的时候不要把别的分支的文件带进来
	不要将图数据文件放进git目录
	修改代码前一定要pull一下，避免冲突

# 其他
	CSR的R数组因为对齐的原因采用32 bits pointer, 32 bits neighbour_count, |..., 其中指针指向的地址要除以64 bits, count也是以64 bits为单位
	C数组也需要64位对齐，空出补0xFFFFFFFF

# Decoupled 一发二收
	发端口ready:=收端口同时ready
	收端口valid:=发端口valid同时两个收端口(另一个）都ready