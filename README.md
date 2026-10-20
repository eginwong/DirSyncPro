How to run DirSync Pro:
  In MS Windows double click on DirSyncPro.exe (or dirsyncpro.jar).
  On all other Operating Systems (such as Linux) run from the command line:
  java -jar dirsyncpro.jar

  If you want to allocate more memory to DirSyncPro, use the following
  command to run DirSync Pro with 512MB (adjust to own will):
  java -Xmx512M -jar dirsyncpro.jar

  The wrapper DirSyncPro.exe itself already uses 512MB memory.

Building and packaging locally:
  Prerequisites:
    - JDK 8 or newer (the project is compiled for Java 8, and newer JDKs such as
      17 work well for local builds)
    - Apache Maven 3.x installed and on your PATH

  From the project root, run the full test suite:
  mvn clean test

  For quieter output during development:
  mvn test -q

  To build the application jar and runtime dependencies:
  mvn clean package

  This creates a runnable jar in the target directory, along with the runtime
  libraries in target/lib/.

  Run the built app directly with:
  java -jar target/dirSyncPro-0.1.jar

  If you want to package a native desktop bundle on macOS, the project includes a
  helper script:
  ./scripts/package-native.sh

  That script runs the Maven build, copies the generated jar and libraries into a
  staging directory, and then uses jpackage to create an app image under dist/mac/.

Requirements:
  Java Runtime Environment (JRE) 1.6.0 or higher. You can download JRE at 
  www.java.com.

Bugs:
  Please report bugs to info@dirsyncpro.org or simply fill in the
  bug form at 
  http://www.dirsyncpro.org/contact.html
  With your help we can make DirSyncPro even better.

  If this program is good for your purpose please: 
  1) Spread the word about DirSync Pro on the internet (blogs, news sites,
     ...) so the program gets more popular.
  2) Consider a donation, any amount you would like as a gesture to let us 
     keep developing this program 
     (please visit: http://www.dirsyncpro.org/donate.html)

Thank you for using DirSync Pro!



## ROADMAP IDEAS

1. update to latest jdk / jre
2. clean up dead code
3. use new java constructs / data structures for efficiency
4. add a formatter
5. compile issues / linting / deprecations
6. can we optimize the size of the executable