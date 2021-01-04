all: compile

compile:
	javac *.java

clean:
	rm -rf temp *.class

# run test cases
test: compile
	java Test

# run test server
test-server: compile
	java TestServer
