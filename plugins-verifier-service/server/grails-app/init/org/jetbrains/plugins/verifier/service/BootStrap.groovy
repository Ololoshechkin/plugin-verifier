package org.jetbrains.plugins.verifier.service

import org.jetbrains.plugins.verifier.service.service.Service
import org.jetbrains.plugins.verifier.service.setting.Settings
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BootStrap {

  private static final Logger LOG = LoggerFactory.getLogger(BootStrap.class)

  private static final int MIN_DISK_SPACE_MB = 10000

  //50% of available disk space is for plugins download dir
  private static final double DOWNLOAD_DIR_PROPORTION = 0.5


  def init = { servletContext ->
    LOG.info("Server is ready to start")

    assertSystemProperties()
    setSystemProperties()

    LOG.info("Server settings: ${Settings.values().collect { it.key + "=" + it.get() }.join(", ")}")
    Service.INSTANCE.run()
  }

  def destroy = {
    LOG.info("Exiting Verifier Service gracefully")
  }

  private static def assertSystemProperties() {
    Settings.values().toList().forEach { setting ->
      try {
        setting.get()
      } catch (IllegalStateException e) {
        throw new IllegalStateException("The property ${setting.key} must be set", e)
      }
    }
  }

  private static void setSystemProperties() {
    String appHomeDir = Settings.APP_HOME_DIRECTORY.get()
    System.setProperty("plugin.verifier.home.dir", appHomeDir + "/verifier")
    System.setProperty("intellij.structure.temp.dir", appHomeDir + "/intellijStructureTmp")
    System.setProperty("plugin.repository.url", Settings.PLUGIN_REPOSITORY_URL.get())

    int diskSpace
    try {
      diskSpace = Integer.parseInt(Settings.MAX_DISK_SPACE_MB.get())
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Max disk space parameter must be set!", e)
    }
    if (diskSpace < MIN_DISK_SPACE_MB) {
      throw new IllegalStateException("Too few available disk space: required at least $MIN_DISK_SPACE_MB Mb")
    }
    int downloadDirSpace = diskSpace * DOWNLOAD_DIR_PROPORTION
    System.setProperty("plugin.verifier.cache.dir.max.space", downloadDirSpace.toString())
  }
}