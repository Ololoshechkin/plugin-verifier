package com.jetbrains.pluginverifier;

import com.jetbrains.pluginverifier.domain.JDK;
import com.jetbrains.pluginverifier.pool.ClassPool;
import com.jetbrains.pluginverifier.pool.ContainerClassPool;
import com.jetbrains.pluginverifier.pool.JarClassPool;
import com.jetbrains.pluginverifier.utils.Util;
import org.apache.commons.cli.CommandLine;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * @author Sergey Evdokimov
 */
public abstract class VerifierCommand {

  private final String name;

  public VerifierCommand(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public abstract void execute(@NotNull CommandLine commandLine, @NotNull List<String> freeArgs) throws Exception;

  protected JDK createJdk(CommandLine commandLine) throws IOException {
    File runtimeDirectory;

    if (commandLine.hasOption('r')) {
      runtimeDirectory = new File(commandLine.getOptionValue('r'));
      if (!runtimeDirectory.isDirectory()) {
        throw Util.fail("Specified runtime directory is not a directory: " + commandLine.getOptionValue('r'));
      }
    }
    else {
      String javaHome = System.getenv("JAVA_HOME");
      if (javaHome == null) {
        throw Util.fail("JAVA_HOME is not specified");
      }

      runtimeDirectory = new File(javaHome);
      if (!runtimeDirectory.isDirectory()) {
        throw Util.fail("Invalid JAVA_HOME: " + javaHome);
      }
    }

    return new JDK(runtimeDirectory);
  }

  protected ClassPool getExternalClassPath(CommandLine commandLine) throws IOException {
    String[] values = commandLine.getOptionValues("cp");
    if (values == null) {
      return null;
    }

    List<ClassPool> pools = new ArrayList<ClassPool>(values.length);

    for (String value : values) {
      pools.add(new JarClassPool(new JarFile(value)));
    }

    return ContainerClassPool.union("external_class_path", pools);
  }

}
