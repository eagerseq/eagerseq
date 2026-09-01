package io.github.eagerseq;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.MULTILINE;

public class ReadmeGenerator {

    /** The source file whose class comment README.md is derived from. */
    static final Path SOURCE = Paths
            .get("src/main/java/io/github/eagerseq/Seq.java");
    static final Path README = Paths.get("README.md");

    private static String prefix = "## Maven\n"
            + "\n"
            + "```\n"
            + "<dependency>\n"
            + "    <groupId>io.github.eagerseq</groupId>\n"
            + "    <artifactId>eagerseq</artifactId>\n"
            + "    <version>x.y.z</version>\n"
            + "</dependency>\n"
            + "```\n"
            + "\n"
            + "## Gradle\n"
            + "\n"
            + "```\n"
            + "implementation 'io.github.eagerseq:eagerseq:x.y.z'\n"
            + "```\n"
            + "\n"
            + "## Introduction\n"
            + "\n";

    public static void main(String[] args) throws IOException {
        Files.write(README, generate().getBytes(StandardCharsets.UTF_8));
        System.out.println("Generated " + README);
    }

    /** Returns the full README text, ending with a newline. */
    public static String generate() throws IOException {
        String lines = SeqStream.viewOf(Files.lines(SOURCE))
                .takeWhile(line -> !line.startsWith(" */"))
                .dropWhile(line -> !line.startsWith(" * "))
                .toString("\n", "", "");
        lines = Pattern.compile("^ \\* ?", MULTILINE)
                .matcher(lines).replaceAll("");
        lines = Pattern.compile("\\{@code ([^}]+)\\}", MULTILINE)
                .matcher(lines).replaceAll("`$1`");
        lines = Pattern.compile("\\{@link ([^} ]+ )?([^}]+)}", MULTILINE)
                .matcher(lines).replaceAll("`$2`");
        lines = Pattern.compile("(\\w+)#(\\w+)", MULTILINE)
                .matcher(lines).replaceAll("$1.$2");
        lines = Pattern.compile("<p>")
                .matcher(lines).replaceAll("");
        lines = Pattern.compile("<h2>(.*)</h2>", MULTILINE)
                .matcher(lines).replaceAll("## $1");
        lines = Pattern.compile("^    ", MULTILINE)
                .matcher(lines).replaceAll("");
        lines = Pattern.compile("<pre>\\{@code")
                .matcher(lines).replaceAll("```java");
        lines = Pattern.compile("}</pre>")
                .matcher(lines).replaceAll("```");
        return prefix + lines + "\n";
    }
}
