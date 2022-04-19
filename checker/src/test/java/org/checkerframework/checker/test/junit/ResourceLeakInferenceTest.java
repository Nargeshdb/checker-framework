package org.checkerframework.checker.test.junit;

import java.io.File;
import java.util.List;
import org.checkerframework.checker.resourceleak.ResourceLeakChecker;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

/** Tests for the Resource Leak Checker. */
public class ResourceLeakInferenceTest extends CheckerFrameworkPerDirectoryTest {
  public ResourceLeakInferenceTest(List<File> testFiles) {
    super(
        testFiles,
        ResourceLeakChecker.class,
        "resourceleak-inference",
        "-Anomsgtext",
        "-Astubs=tests/resourceleak-inference",
        "-Ainfer=stubs",
        "-AmergeStubsWithSource",
        "-AstubWarnIfNotFound",
        "-Awarns");
  }

  @Parameters
  public static String[] getTestDirs() {
    return new String[] {"resourceleak-inference"};
  }
}
