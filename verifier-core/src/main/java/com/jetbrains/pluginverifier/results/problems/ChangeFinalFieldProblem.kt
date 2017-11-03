package com.jetbrains.pluginverifier.results.problems

import com.jetbrains.pluginverifier.misc.formatMessage
import com.jetbrains.pluginverifier.results.instruction.Instruction
import com.jetbrains.pluginverifier.results.location.FieldLocation
import com.jetbrains.pluginverifier.results.location.MethodLocation

data class ChangeFinalFieldProblem(val field: FieldLocation,
                                   val accessor: MethodLocation,
                                   val instruction: Instruction) : Problem() {

  override val shortDescription = "Attempt to change a final field {0}".formatMessage(field)

  override val fullDescription = "Method {0} has modifying instruction *{1}* referencing a final field {2}. This can lead to **IllegalAccessError** exception at runtime.".formatMessage(accessor, instruction, field)

}