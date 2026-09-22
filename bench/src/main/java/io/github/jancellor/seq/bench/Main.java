package io.github.jancellor.seq.bench;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Entry point of the benchmark jar. Accepts the usual JMH command line,
 * adds the GC profiler and a fixed heap, runs, and writes the results as
 * markdown to {@code RESULTS.md} in the working directory (or the path in
 * the {@code results} system property).
 */
public class Main {

    private static final List<String> IMPLS = List.of("LOOP", "JDK",
            "SEQSTREAM", "SEQ");

    public static void main(String[] args) throws Exception {
        CommandLineOptions cmdline = new CommandLineOptions(args);
        if (cmdline.shouldHelp()) {
            cmdline.showHelp();
            return;
        }
        Options options = new OptionsBuilder()
                .parent(cmdline)
                .addProfiler(GCProfiler.class)
                .jvmArgsPrepend("-Xms2g", "-Xmx2g", "-XX:+AlwaysPreTouch")
                .build();
        Collection<RunResult> results = new Runner(options).run();
        Path out = Path.of(System.getProperty("results", "RESULTS.md"));
        writeMarkdown(results, out);
        System.out.println("Markdown written to " + out.toAbsolutePath());
    }

    private static void writeMarkdown(Collection<RunResult> results, Path out)
            throws IOException {
        // benchmark -> size -> impl -> [time, alloc, error]
        Map<String, Map<Integer, Map<String, double[]>>> pipelineCells = new TreeMap<>();
        Map<String, Map<Integer, Map<String, double[]>>> operationCells = new TreeMap<>();
        for (RunResult r : results) {
            String benchmark = r.getParams().getBenchmark();
            int methodCut = benchmark.lastIndexOf('.');
            String owner = benchmark.substring(0, methodCut);
            String benchmarkClass = owner.substring(owner.lastIndexOf('.') + 1);
            String name = benchmark.substring(methodCut + 1);
            int size = Integer.parseInt(r.getParams().getParam("size"));
            String impl = r.getParams().getParam("impl");
            Map<String, Map<Integer, Map<String, double[]>>> cells;
            if (benchmarkClass.equals("OperationBench")) {
                // OperationBench names its pairs name_seq and name_jdk instead.
                int cut = name.lastIndexOf('_');
                if (name.endsWith("_seq")) {
                    impl = "SEQSTREAM";
                } else if (name.endsWith("_jdk")) {
                    impl = "JDK";
                } else {
                    throw new IllegalArgumentException(
                            "unpaired OperationBench method: " + name);
                }
                name = name.substring(0, cut);
                cells = operationCells;
            } else if (benchmarkClass.equals("PipelineBench")) {
                cells = pipelineCells;
            } else {
                throw new IllegalArgumentException(
                        "unknown benchmark class: " + benchmarkClass);
            }
            Result<?> alloc = r.getSecondaryResults().get("gc.alloc.rate.norm");
            cells.computeIfAbsent(name, k -> new TreeMap<>())
                    .computeIfAbsent(size, k -> new TreeMap<>())
                    .put(impl, new double[]{
                            r.getPrimaryResult().getScore(),
                            alloc == null ? Double.NaN : alloc.getScore(),
                            r.getPrimaryResult().getScoreError()});
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("# Benchmark results");
            w.println();
            w.println(LocalDate.now() + ", "
                    + System.getProperty("java.vm.name")
                    + " " + System.getProperty("java.vm.version")
                    + ". Cells are us/op / B/op. See README.md for how to read them.");
            w.println();
            if (!pipelineCells.isEmpty()) {
                w.println("## PipelineBench: pipeline shapes");
                w.println();
                writePipeline(w, pipelineCells);
            }
            if (!operationCells.isEmpty()) {
                w.println("## OperationBench: operator pairs");
                w.println();
                writePaired(w, operationCells);
            }
        }
    }

    private static void writePipeline(
            PrintWriter w,
            Map<String, Map<Integer, Map<String, double[]>>> cells) {
        for (Map.Entry<String, Map<Integer, Map<String, double[]>>> b : cells
                .entrySet()) {
            w.println("### " + b.getKey());
            w.println();
            w.println("| size | " + String.join(" | ", IMPLS) + " |");
            w.println("|---:|" + "---:|".repeat(IMPLS.size()));
            for (Map.Entry<Integer, Map<String, double[]>> row : b.getValue()
                    .entrySet()) {
                StringBuilder line = new StringBuilder(
                        "| " + String.format("%,d", row.getKey()) + " |");
                for (String impl : IMPLS) {
                    line.append(' ').append(cell(row.getValue().get(impl)))
                            .append(" |");
                }
                w.println(line);
            }
            w.println();
        }
    }

    /**
     * One table per size, a row per case, ordered by the SeqStream to JDK
     * time ratio so divergences sort to the top.
     */
    private static void writePaired(
            PrintWriter w,
            Map<String, Map<Integer, Map<String, double[]>>> cells) {
        Map<Integer, Map<String, Map<String, double[]>>> bySize = new TreeMap<>();
        cells.forEach((name, sizes) -> sizes.forEach((size, impls) -> bySize
                .computeIfAbsent(size, k -> new TreeMap<>()).put(name, impls)));
        for (Map.Entry<Integer, Map<String, Map<String, double[]>>> e : bySize
                .entrySet()) {
            w.println("### size " + String.format("%,d", e.getKey()));
            w.println();
            w.println(
                    "| case | JDK us/op | Seq us/op | time x | +/- | JDK B/op | Seq B/op | alloc x |");
            w.println("|---|---:|---:|---:|---:|---:|---:|---:|");
            e.getValue().entrySet().stream()
                    .sorted(Comparator.comparingDouble(
                            (Map.Entry<String, Map<String, double[]>> row) -> -ratio(
                                    row.getValue(), 0)))
                    .forEach(row -> {
                        double[] jdk = row.getValue().get("JDK");
                        double[] seq = row.getValue().get("SEQSTREAM");
                        w.println("| " + row.getKey()
                                + " | " + number(jdk, 0) + " | "
                                + number(seq, 0)
                                + " | " + times(ratio(row.getValue(), 0))
                                + " | " + error(jdk, seq)
                                + " | " + number(jdk, 1) + " | "
                                + number(seq, 1)
                                + " | " + times(ratio(row.getValue(), 1))
                                + " |");
                    });
            w.println();
        }
    }

    private static double ratio(Map<String, double[]> row, int index) {
        double[] jdk = row.get("JDK");
        double[] seq = row.get("SEQSTREAM");
        if (jdk == null || seq == null || jdk[index] == 0) {
            return Double.NaN;
        }
        return seq[index] / jdk[index];
    }

    private static String number(double[] c, int index) {
        if (c == null || Double.isNaN(c[index])) {
            return "-";
        }
        double value = c[index];
        return value < 100 && index == 0
                ? String.format("%,.2f", value)
                : String.format("%,.0f", value);
    }

    /**
     * The larger of the two sides' relative errors, as a fraction of the
     * ratio. A ratio is only worth reading when this is small.
     */
    private static String error(double[] jdk, double[] seq) {
        if (jdk == null || seq == null
                || Double.isNaN(jdk[2]) || Double.isNaN(seq[2])) {
            return "-";
        }
        double worst = Math.max(jdk[2] / jdk[0], seq[2] / seq[0]);
        return String.format("%.0f%%", worst * 100);
    }

    private static String times(double ratio) {
        return Double.isNaN(ratio) ? "-" : String.format("%.2f", ratio);
    }

    private static String cell(double[] c) {
        if (c == null) {
            return "-";
        }
        String time = c[0] < 100 ? String.format("%,.2f", c[0])
                : String.format("%,.0f", c[0]);
        return Double.isNaN(c[1]) ? time
                : time + " / " + String.format("%,.0f", c[1]);
    }
}
