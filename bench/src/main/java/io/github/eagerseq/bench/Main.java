package io.github.eagerseq.bench;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.CommandLineOptions;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Entry point of the benchmark jar. Accepts the usual JMH command line,
 * adds the GC profiler and a fixed heap, runs, and writes the results as
 * markdown to {@code RESULTS.md} in the working directory (or the path in
 * the {@code results} system property).
 */
public class Main {

    private static final List<String> IMPLS = List.of("LOOP", "JDK", "SEQSTREAM", "SEQ");

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

    private static void writeMarkdown(Collection<RunResult> results, Path out) throws IOException {
        // benchmark -> size -> impl -> [time, alloc]
        Map<String, Map<Integer, Map<String, double[]>>> cells = new TreeMap<>();
        for (RunResult r : results) {
            String benchmark = r.getParams().getBenchmark();
            String name = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            int size = Integer.parseInt(r.getParams().getParam("size"));
            String impl = r.getParams().getParam("impl");
            Result<?> alloc = r.getSecondaryResults().get("gc.alloc.rate.norm");
            cells.computeIfAbsent(name, k -> new TreeMap<>())
                    .computeIfAbsent(size, k -> new TreeMap<>())
                    .put(impl, new double[] {
                            r.getPrimaryResult().getScore(),
                            alloc == null ? Double.NaN : alloc.getScore() });
        }
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println("# Benchmark results");
            w.println();
            w.println(LocalDate.now() + ", " + System.getProperty("java.vm.name")
                    + " " + System.getProperty("java.vm.version")
                    + ". Cells are us/op / B/op. See README.md for how to read them.");
            w.println();
            for (Map.Entry<String, Map<Integer, Map<String, double[]>>> b : cells.entrySet()) {
                w.println("### " + b.getKey());
                w.println();
                w.println("| size | " + String.join(" | ", IMPLS) + " |");
                w.println("|---:|" + "---:|".repeat(IMPLS.size()));
                for (Map.Entry<Integer, Map<String, double[]>> row : b.getValue().entrySet()) {
                    StringBuilder line = new StringBuilder("| " + String.format("%,d", row.getKey()) + " |");
                    for (String impl : IMPLS) {
                        line.append(' ').append(cell(row.getValue().get(impl))).append(" |");
                    }
                    w.println(line);
                }
                w.println();
            }
        }
    }

    private static String cell(double[] c) {
        if (c == null) {
            return "-";
        }
        String time = c[0] < 100 ? String.format("%,.2f", c[0]) : String.format("%,.0f", c[0]);
        return Double.isNaN(c[1]) ? time : time + " / " + String.format("%,.0f", c[1]);
    }
}
