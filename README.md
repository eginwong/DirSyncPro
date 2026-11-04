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

  Optimization work and benchmark results are tracked in [OPTIMIZATION_ASSESSMENT.md](OPTIMIZATION_ASSESSMENT.md).

  Java formatting:
    - Run `mvn spotless:apply` to format Java sources manually.
    - Run `mvn spotless:check` to verify formatting without changing files.
    - Enable the repository pre-commit hook with `./scripts/setup-git-hooks.sh`.
      The hook formats Java sources before a commit and re-stages the staged Java files.

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

1. jdk 17 -> 21
2. use new java constructs / data structures for efficiency
3. add a formatter (configured with Spotless/Google Java Format)
4. compile issues / linting / deprecations
5. can we optimize the size of the executable
6. can we remove or clean up any unused icons in resources