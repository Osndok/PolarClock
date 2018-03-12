
run: all
	java -cp build PolarClock

all: src/PolarClock.java
	rm -rf build
	mkdir build
	javac -d build $^

