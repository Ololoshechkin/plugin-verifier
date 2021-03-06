package kotlin.com.jetbrains.plugin.structure.ktor.problems

import com.jetbrains.plugin.structure.base.problems.InvalidDescriptorProblem
import com.jetbrains.plugin.structure.ktor.bean.GradleRepositoryType

class GradleRepoIncorrectDescription(expectedField: String, unexpectedField) : InvalidDescriptorProblem(null) {

  override val detailedMessage
    get() = "Gradle repository description is incorrect: expected only $expectedField to be set. " +
      "Please, delete $unexpectedField from the descriptor"

  override val level
    get() = Level.ERROR

}