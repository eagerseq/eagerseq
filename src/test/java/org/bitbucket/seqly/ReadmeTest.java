package org.bitbucket.seqly;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks that README.md still matches the class comment on {@link Seq}, from
 * which {@link ReadmeGenerator} derives it.
 */
public class ReadmeTest {

    @Test
    public void readmeMatchesSeqClassComment() throws IOException {
        // Both paths are relative to the project root, which is where Maven
        // runs the tests from.
        assertTrue(
                "Expected to run from the project root, but "
                        + ReadmeGenerator.SOURCE
                        + " does not exist under the working directory "
                        + Paths.get("").toAbsolutePath(),
                Files.isRegularFile(ReadmeGenerator.SOURCE));
        String expected = ReadmeGenerator.generate();
        String actual = new String(Files.readAllBytes(ReadmeGenerator.README),
                StandardCharsets.UTF_8);
        assertEquals("README.md is out of date. Regenerate it with:"
                + System.lineSeparator()
                + "java -cp target/test-classes:target/classes "
                + ReadmeGenerator.class.getName(),
                expected, actual);
    }
}
