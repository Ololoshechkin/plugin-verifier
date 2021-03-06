/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.ktor

import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.problems.PropertyNotSpecified
import com.jetbrains.plugin.structure.ktor.bean.*
import com.jetbrains.plugin.structure.ktor.bean.GradleRepositoryType
import kotlin.com.jetbrains.plugin.structure.ktor.problems.DocumentationContainsResource
import kotlin.com.jetbrains.plugin.structure.ktor.problems.GradleRepoIncorrectDescription

internal fun validateKtorPluginBean(descriptor: KtorFeatureDescriptor): List<PluginProblem> {
  val problems = mutableListOf<PluginProblem>()
  val vendor = descriptor.vendor
  if (vendor == null || vendor.name.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(VENDOR))
  }
  if (descriptor.pluginName.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(NAME))
  }
  if (descriptor.pluginId.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(ID))
  }
  if (descriptor.pluginVersion.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(VERSION))
  }

  descriptor.documentation?.let { documentation ->
    if (documentation.description.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(DOCUMENTATION_DESCRIPTION))
    }
    if (documentation.usage.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(DOCUMENTATION_USAGE))
    }
    if (documentation.options.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(DOCUMENTATION_OPTIONS))
    }

    if (documentation.description?.contains("\!\[.*\]\(".toRegex())) {
      problems.add(DocumentationContainsResource("description"))
    }
    if (documentation.usage?.contains("\!\[.*\]\(".toRegex())) {
      problems.add(DocumentationContainsResource("usage"))
    }
    if (documentation.options?.contains("\!\[.*\]\(".toRegex())) {
      problems.add(DocumentationContainsResource("options"))
    }
  }

  descriptor.installReceipt?.extraTemplates?.any { codeTemplate ->
    if (codeTemplate.position == null) {
      problems.add(PropertyNotSpecified(TEMPLATE_POSITION))
    }
    if (codeTemplate.text.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(TEMPLATE_TEXT))
    }
  }

  descriptor.testInstallReceipt?.extraTemplates?.any { codeTemplate ->
    if (codeTemplate.position == null) {
      problems.add(PropertyNotSpecified(TEMPLATE_POSITION))
    }
    if (codeTemplate.text.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(TEMPLATE_TEXT))
    }
  }

  (descriptor.gradleInstall?.dependencies.orEmpty()
    + descriptor.mavenInstall?.dependencies.orEmpty()).any { dependency ->
    if (dependency.group.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(DEPENDENCY_GROUP))
    }
    if (dependency.artifact.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(DEPENDENCY_ARTIFACT))
    }
    if (dependency.version == "") {
      problems.add(PropertyNotSpecified(DEPENDENCY_VERSION))
    }
  }

  descriptor.mavenInstall?.repositories.any { repo ->
    if (repo.id.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(MAVEN_REP_ID))
    }
    if (repo.url.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(MAVEN_REP_URL))
    }
  }

  descriptor.gradleInstall?.repositories.any { repo ->
    if (repo.type.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(GRADLE_REP_TYPE))
    }
    if (repo.type == GradleRepositoryType.FUNCTION && repo.functionName.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(GRADLE_REP_FUNCTION))
    }
    if (repo.type == GradleRepositoryType.FUNCTION && !repo.url.isNullOrBlank()) {
      problems.add(
        GradleRepoIncorrectDescription(
          expectedField = GRADLE_REP_FUNCTION,
          unexpectedField = GRADLE_REP_URL
        )
      )
    }
    if (repo.type == GradleRepositoryType.URL && repo.url.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(GRADLE_REP_URL))
    }
    if (repo.type == GradleRepositoryType.FUNCTION && !repo.functionName.isNullOrBlank()) {
      problems.add(
        GradleRepoIncorrectDescription(
          expectedField = GRADLE_REP_URL,
          unexpectedField = GRADLE_REP_FUNCTION
        )
      )
    }
  }

  descriptor.mavenInstall?.repositories.any { repo ->
    if (repo.id.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(MAVEN_REP_ID))
    }
    if (repo.url.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(MAVEN_REP_URL))
    }
  }

  descriptor.gradleInstall?.plugins.any { plugin ->
    if (plugin.id.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(PLUGIN_ID))
    }
  }

  descriptor.mavenInstall?.plugins.any { plugin ->
    if (plugin.group.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(PLUGIN_GROUP))
    }
    if (plugin.artifact.isNullOrBlank()) {
      problems.add(PropertyNotSpecified(PLUGIN_ARTIFACT))
    }
  }

  return problems
}
