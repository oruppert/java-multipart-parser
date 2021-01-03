all: compile

compile:
	javac *.java

clean:
	rm -f *.class

# run test cases
test: compile
	java Test

# run test server
test-server: compile
	java Server
