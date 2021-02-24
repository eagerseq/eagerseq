package org.bitbucket.seqly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.MULTILINE;

public class ReadmeGenerator {

    private static String prefix = "## Maven\n"
            + "\n"
            + "```\n"
            + "<dependency>\n"
            + "    <groupId>org.bitbucket.seqly</groupId>\n"
            + "    <artifactId>seqly</artifactId>\n"
            + "    <version>x.y.z</version>\n"
            + "</dependency>\n"
            + "```\n"
            + "\n"
            + "## Gradle\n"
            + "\n"
            + "```\n"
            + "implementation 'org.bitbucket.seqly:seqly:x.y.z'\n"
            + "```\n"
            + "\n"
            + "## Introduction\n"
            + "\n";

    public static void main(String[] args) throws IOException {
        String lines = SeqStream.view(Files.lines(
                Paths.get("src/main/java/org/bitbucket/seqly/Seq.java")))
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
        lines = prefix + lines;
        System.out.println(lines);
    }
}
