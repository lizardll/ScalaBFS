CC:=g++
transfer=transfer.cpp
generate=GraphToScalaBFS.cpp

all : transfer GraphToScalaBFS

transfer:$(transfer)
	${CC} $(transfer) -o transfer

GraphToScalaBFS:$(generate)
	${CC} $(generate) -o GraphToScalaBFS

clean:
	rm -f *.o
	rm -f transfer
	rm -f GraphToScalaBFS