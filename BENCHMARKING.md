# DirSyncPro performance benchmarking

## Goal
Measure the current cost of the sync analyzer before optimization, then rerun the same benchmark after performance work to compare the effect on runtime and queue buildup.

## How to run
From the project root:

```bash
export JAVA_HOME=/tmp/java-install/jdk-17.0.20+8/Contents/Home
cd /Users/ericwong/dev/DirSyncPro
mkdir -p /tmp/dsp-test-classes
curl -fsSL -o /tmp/javax.annotation-api.jar 'https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar'
curl -fsSL -o /tmp/junit-platform-console-standalone.jar 'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar'
find src/main/java src/test/java -name '*.java' > /tmp/dsp-sources.txt
"$JAVA_HOME/bin/javac" -cp /tmp/javax.annotation-api.jar:/tmp/junit-platform-console-standalone.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources -d /tmp/dsp-test-classes @/tmp/dsp-sources.txt
"$JAVA_HOME/bin/java" -cp /tmp/dsp-test-classes:/tmp/javax.annotation-api.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources dirsyncpro.job.JobAnalyzeBenchmark
```

## What it measures
- total analyze time for each scenario
- average, min, and max runtime across 5 iterations
- queue size after analysis for the selected dataset

## Baseline and optimization results

### Benchmark command used
```bash
export JAVA_HOME=/tmp/java-install/jdk-17.0.20+8/Contents/Home
cd /Users/ericwong/dev/DirSyncPro
mkdir -p /tmp/dsp-test-classes
curl -fsSL -o /tmp/javax.annotation-api.jar 'https://repo1.maven.org/maven2/javax/annotation/javax.annotation-api/1.3.2/javax.annotation-api-1.3.2.jar'
curl -fsSL -o /tmp/junit-platform-console-standalone.jar 'https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar'
find src/main/java src/test/java -name '*.java' > /tmp/dsp-sources.txt
"$JAVA_HOME/bin/javac" -cp /tmp/javax.annotation-api.jar:/tmp/junit-platform-console-standalone.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources -d /tmp/dsp-test-classes @/tmp/dsp-sources.txt
"$JAVA_HOME/bin/java" -cp /tmp/dsp-test-classes:/tmp/javax.annotation-api.jar:/Users/ericwong/dev/DirSyncPro/src/main/resources dirsyncpro.job.JobAnalyzeBenchmark
```

### Before optimization

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 405 ms | 211 ms | 975 ms | 134 |
| 500 files | 580 ms | 402 ms | 684 ms | 660 |
| 1500 files | 1319 ms | 1025 ms | 1520 ms | 1952 |

### After the first optimization pass

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 405 ms | 129 ms | 1312 ms | 134 |
| 500 files | 586 ms | 446 ms | 781 ms | 660 |
| 1500 files | 2126 ms | 1683 ms | 2648 ms | 1952 |

### Comparison of the first optimization pass

| Dataset | Delta avg | Notes |
|---|---:|---|
| 100 files | 0 ms | effectively flat, but highly variable |
| 500 files | +6 ms (+1.0%) | slightly slower |
| 1500 files | +807 ms (+61.1%) | materially slower |

### Current benchmark baseline for the next experiment
The current state before the second experiment is the fresh run captured immediately before this change.

| Dataset | Average | Min | Max | Queue size |
|---|---:|---:|---:|---:|
| 100 files | 120 ms | 39 ms | 388 ms | 134 |
| 500 files | 129 ms | 110 ms | 151 ms | 660 |
| 1500 files | 360 ms | 281 ms | 458 ms | 1952 |

### Conclusion
The first optimization pass in [src/main/java/job/Job.java](src/main/java/job/Job.java) was not a net win under these synthetic workloads. The tree walk still performs the same number of expensive per-file comparisons, and the benchmark shows the main cost is in pair analysis rather than repeated filter calls alone.

This means the next optimization should target the hot path deeper: reducing redundant per-file comparison work, avoiding repeated expensive metadata and path-replacement checks, and pruning comparison branches before they create full SyncPair objects.
