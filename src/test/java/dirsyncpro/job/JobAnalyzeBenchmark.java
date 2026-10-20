package dirsyncpro.job;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import dirsyncpro.Const;
import dirsyncpro.DirSyncPro;
import dirsyncpro.sync.Sync;

public class JobAnalyzeBenchmark {
    private static final int[] FILE_COUNTS = {100, 500, 1500};
    private static final int ITERATIONS = 5;

    public static void main(String[] args) throws Exception {
        Path benchmarkRoot = Files.createTempDirectory("dsp-benchmark");
        System.out.println("Benchmark root: " + benchmarkRoot);

        for (int fileCount : FILE_COUNTS) {
            runScenario(benchmarkRoot, fileCount);
        }
    }

    private static void runScenario(Path benchmarkRoot, int fileCount) throws Exception {
        System.out.println("\n=== Scenario: " + fileCount + " files ===");
        Path homeDir = benchmarkRoot.resolve("home-" + fileCount);
        Files.createDirectories(homeDir);

        System.setProperty("dirsyncpro.home", homeDir.toString());
        DirSyncPro.loadProperties();
        DirSyncPro.setUpLogFile(false);

        long[] timings = new long[ITERATIONS];
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            Path sourceRoot = benchmarkRoot.resolve("source-" + fileCount + "-" + iteration);
            Path targetRoot = benchmarkRoot.resolve("target-" + fileCount + "-" + iteration);
            createDataset(sourceRoot, targetRoot, fileCount);

            Sync sync = new Sync();
            Field syncDateField = Sync.class.getDeclaredField("syncDate");
            syncDateField.setAccessible(true);
            syncDateField.set(sync, new Date());
            DirSyncPro.setSync(sync);

            Job job = new Job(true);
            job.setName("benchmark-job-" + fileCount + "-" + iteration);
            job.setSrc(sourceRoot.toString());
            job.setDst(targetRoot.toString());
            job.setSyncMode(Const.SyncMode.ABMirror);

            long start = System.nanoTime();
            job.analyze();
            long end = System.nanoTime();
            timings[iteration] = (end - start) / 1_000_000L;

            System.out.println("iteration " + (iteration + 1) + ": " + timings[iteration] + " ms, queue=" + sync.getSyncQ().size());
        }

        long average = average(timings);
        long min = minimum(timings);
        long max = maximum(timings);
        System.out.println("average=" + average + " ms, min=" + min + " ms, max=" + max + " ms");
    }

    private static void createDataset(Path sourceRoot, Path targetRoot, int fileCount) throws Exception {
        Files.createDirectories(sourceRoot);
        Files.createDirectories(targetRoot);

        List<Path> sourceDirs = new ArrayList<>();
        for (int i = 0; i < Math.max(3, fileCount / 40); i++) {
            Path dir = sourceRoot.resolve("folder-" + i);
            Files.createDirectories(dir);
            sourceDirs.add(dir);
        }

        for (int i = 0; i < fileCount; i++) {
            Path sourceDir = sourceDirs.get(i % sourceDirs.size());
            Path sourceFile = sourceDir.resolve("file-" + i + ".txt");
            Files.writeString(sourceFile, "benchmark data for file " + i + "\n" + "line 2\nline 3\n");

            if (i % 3 == 0) {
                Path targetDir = targetRoot.resolve(sourceDir.getFileName().toString());
                Files.createDirectories(targetDir);
                Path targetFile = targetDir.resolve("file-" + i + ".txt");
                Files.writeString(targetFile, "benchmark data for file " + i + "\n" + "line 2\nline 3\n");
                Files.setLastModifiedTime(targetFile, FileTime.fromMillis(System.currentTimeMillis() - 5_000L));
            }

            if (i % 11 == 0) {
                Path orphanDir = targetRoot.resolve("orphan-" + i);
                Files.createDirectories(orphanDir);
                Files.writeString(orphanDir.resolve("stale.txt"), "orphan");
            }

            if (i % 17 == 0) {
                Path sourceOnlyDir = sourceRoot.resolve("source-only-" + i);
                Files.createDirectories(sourceOnlyDir);
                Files.writeString(sourceOnlyDir.resolve("new.txt"), "new file");
            }
        }
    }

    private static long average(long[] values) {
        long total = 0L;
        for (long value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static long minimum(long[] values) {
        long min = Long.MAX_VALUE;
        for (long value : values) {
            min = Math.min(min, value);
        }
        return min;
    }

    private static long maximum(long[] values) {
        long max = Long.MIN_VALUE;
        for (long value : values) {
            max = Math.max(max, value);
        }
        return max;
    }
}
