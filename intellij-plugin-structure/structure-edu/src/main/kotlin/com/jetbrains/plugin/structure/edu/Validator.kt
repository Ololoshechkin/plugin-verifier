/*
 * Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.jetbrains.plugin.structure.edu

import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.base.problems.PropertyNotSpecified
import com.jetbrains.plugin.structure.edu.bean.EduPluginDescriptor
import com.jetbrains.plugin.structure.edu.problems.InvalidVersionError
import com.jetbrains.plugin.structure.edu.problems.Language
import com.jetbrains.plugin.structure.edu.problems.UnsupportedLanguage
import com.jetbrains.plugin.structure.edu.problems.UnsupportedProgrammingLanguage
import java.util.*

internal fun validateEduPluginBean(descriptor: EduPluginDescriptor): List<PluginProblem> {
  val problems = mutableListOf<PluginProblem>()
  if (descriptor.title.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(TITLE))
  }
  if (descriptor.summary.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(SUMMARY))
  }
  if (descriptor.items == null || descriptor.items.isEmpty()) {
    problems.add(PropertyNotSpecified(ITEMS))
  }
  val vendor = descriptor.vendor
  if (vendor == null || vendor.name.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(VENDOR))
  }
  validateLanguage(descriptor, problems)
  validateProgrammingLanguage(descriptor, problems)
  validatePluginVersion(descriptor, problems)
  return problems
}

private fun validateProgrammingLanguage(descriptor: EduPluginDescriptor, problems: MutableList<PluginProblem>) {
  if (descriptor.programmingLanguage.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(PROGRAMMING_LANGUAGE))
    return
  }
  if (descriptor.programmingLanguage !in Language.values().map { it.id }) {
    problems.add(UnsupportedProgrammingLanguage)
  }
}

private fun validateLanguage(descriptor: EduPluginDescriptor, problems: MutableList<PluginProblem>) {
  if (descriptor.language.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(LANGUAGE))
    return
  }
  if (Locale.getISOLanguages().find { it == descriptor.language } == null) {
    problems.add(UnsupportedLanguage(descriptor.language))
  }
}

private fun validatePluginVersion(descriptor: EduPluginDescriptor, problems: MutableList<PluginProblem>) {
  if (descriptor.eduPluginVersion.isNullOrBlank()) {
    problems.add(PropertyNotSpecified(EDU_PLUGIN_VERSION))
    return
  }
  val versionComponents = descriptor.eduPluginVersion.split("-")
  if (versionComponents.size != 3) {
    problems.add(InvalidVersionError(descriptor.eduPluginVersion))
  }
}