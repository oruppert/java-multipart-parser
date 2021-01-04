all: test

test: compile
	java -cp '.:lib/*' Test

compile:
	javac -cp 'lib/*' *.java

clean:
	rm -rf temp *.class



