package com.jetbrains.pluginverifier.verifiers.instruction

import com.intellij.structure.resolvers.Resolver
import com.jetbrains.pluginverifier.api.VContext
import com.jetbrains.pluginverifier.location.ProblemLocation
import com.jetbrains.pluginverifier.problems.*
import com.jetbrains.pluginverifier.reference.ClassReference
import com.jetbrains.pluginverifier.reference.MethodReference
import com.jetbrains.pluginverifier.utils.VerifierUtil
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import java.util.*

class InvokeInstructionVerifier : InstructionVerifier {
  override fun verify(clazz: ClassNode, method: MethodNode, instr: AbstractInsnNode, resolver: Resolver, ctx: VContext) {
    if (instr is MethodInsnNode) {
      InvokeImplementation(clazz, method, instr, resolver, ctx).verify()
    }
  }

}

@Suppress("UNCHECKED_CAST")
private class InvokeImplementation(val verifiableClass: ClassNode,
                                   val verifiableMethod: MethodNode,
                                   val instr: MethodInsnNode,
                                   val resolver: Resolver,
                                   val ctx: VContext,
                                   val methodOwner: String = instr.owner,
                                   val methodName: String = instr.name,
                                   val methodDescriptor: String = instr.desc) {
  var ownerNode: ClassNode? = null

  fun verify() {
    if (methodOwner.startsWith("[")) {
      val arrayType = VerifierUtil.extractClassNameFromDescr(methodOwner)
      if (arrayType != null) {
        VerifierUtil.checkClassExistsOrExternal(resolver, arrayType, ctx, { getFromMethod() })
      }
      return
    }
    ownerNode = VerifierUtil.resolveClassOrProblem(resolver, methodOwner, verifiableClass, ctx, { getFromMethod() }) ?: return

    when (instr.opcode) {
      Opcodes.INVOKEVIRTUAL -> processInvokeVirtual()
      Opcodes.INVOKESPECIAL -> processInvokeSpecial()
      Opcodes.INVOKEINTERFACE -> processInvokeInterface()
      Opcodes.INVOKESTATIC -> processInvokeStatic()
      else -> throw RuntimeException("Unknown opcode for MethodInsnNode ${instr.opcode}: $instr")
    }
  }

  private fun processInvokeVirtual() {
    val resolved = resolveClassMethod() ?: return

    if (VerifierUtil.isStatic(resolved.methodNode)) {
      /*
      Otherwise, if the resolved method is a class (static) method, the invokevirtual instruction throws an IncompatibleClassChangeError.
       */
      ctx.registerProblem(InvokeVirtualOnStaticMethodProblem(MethodReference.from(resolved.definingClass, resolved.methodNode)), getFromMethod())
    }
  }

  private fun processInvokeSpecial() {
    val resolved: ResolvedMethod
    if (instr.itf) {
      resolved = resolveInterfaceMethod() ?: return
    } else {
      resolved = resolveClassMethod() ?: return
    }

    /*
    Otherwise, if the resolved method is a class (static) method,
    the invokespecial instruction throws an IncompatibleClassChangeError.
     */
    if (VerifierUtil.isStatic(resolved.methodNode)) {
      ctx.registerProblem(InvokeSpecialOnStaticMethodProblem(MethodReference(methodOwner, methodName, methodDescriptor)), getFromMethod())
    }
  }

  private fun processInvokeInterface() {
    val resolved = resolveInterfaceMethod() ?: return

    /*
    Otherwise, if the resolved method is static or private, the invokeinterface instruction throws an IncompatibleClassChangeError.
     */
    if (VerifierUtil.isPrivate(resolved.methodNode)) {
      ctx.registerProblem(InvokeInterfaceOnPrivateMethodProblem(MethodReference(methodOwner, methodName, methodDescriptor)), getFromMethod())
    }
    if (VerifierUtil.isStatic(resolved.methodNode)) {
      ctx.registerProblem(InvokeInterfaceOnStaticMethodProblem(MethodReference(methodOwner, methodName, methodDescriptor)), getFromMethod())
    }
  }

  private fun processInvokeStatic() {
    val resolved = resolveClassMethod() ?: return

    /*
    Otherwise, if the resolved method is an instance method, the invokestatic instruction throws an IncompatibleClassChangeError.
     */
    if (!VerifierUtil.isStatic(resolved.methodNode)) {
      ctx.registerProblem(InvokeStaticOnInstanceMethodProblem(MethodReference.from(resolved.definingClass, resolved.methodNode)), getFromMethod())
    }
  }

  private fun getFromMethod() = ProblemLocation.fromMethod(verifiableClass.name, verifiableMethod)

  fun resolveInterfaceMethod(): ResolvedMethod? {
    val (fail, resolvedMethod) = resolveInterfaceMethod0(ownerNode!!)
    if (fail) {
      return null
    }

    if (resolvedMethod == null) {
      ctx.registerProblem(MethodNotFoundProblem(methodOwner, methodName, methodDescriptor), getFromMethod())
      return null
    } else {
      /*
       * Otherwise, if method lookup succeeds and the referenced method is not accessible (§5.4.4) to D,
       * method resolution throws an IllegalAccessError.
       */
      checkMethodIsAccessible(resolvedMethod)
    }
    return resolvedMethod
  }

  fun resolveClassMethod(): ResolvedMethod? {
    val (fail, resolvedMethod) = resolveClassMethod0(ownerNode!!)
    if (fail) {
      return null
    }

    if (resolvedMethod == null) {
      ctx.registerProblem(MethodNotFoundProblem(methodOwner, methodName, methodDescriptor), getFromMethod())
      return null
    } else {
      /*
       * Otherwise, if method lookup succeeds and the referenced method is not accessible (§5.4.4) to D,
       * method resolution throws an IllegalAccessError.
       */
      checkMethodIsAccessible(resolvedMethod)
    }
    return resolvedMethod
  }

  /**
   * A field or method R is accessible to a class or interface D if and only if any of the following is true:
   * - R is public.
   * - R is protected and is declared in a class C, and D is either a subclass of C or C itself.
   * Furthermore, if R is not static, then the symbolic reference to R must contain a symbolic reference
   * to a class T, such that T is either a subclass of D, a superclass of D, or D itself.
   * - R is either protected or has default access (that is, neither public nor protected nor private),
   * and is declared by a class in the same run-time package as D.
   * - R is private and is declared in D.
   */
  fun checkMethodIsAccessible(location: ResolvedMethod) {
    val definingClass = location.definingClass
    val methodNode = location.methodNode

    var accessProblem: AccessType? = null

    if (VerifierUtil.isPrivate(methodNode)) {
      if (verifiableClass.name != definingClass.name) {
        //accessing to private method of the other class
        accessProblem = AccessType.PRIVATE
      }
    } else if (VerifierUtil.isProtected(methodNode)) {
      if (!VerifierUtil.haveTheSamePackage(verifiableClass, definingClass)) {
        if (!VerifierUtil.isSubclassOf(verifiableClass, definingClass, resolver, ctx)) {
          accessProblem = AccessType.PROTECTED
        }
      }

    } else if (VerifierUtil.isDefaultAccess(methodNode)) {
      if (!VerifierUtil.haveTheSamePackage(definingClass, verifiableClass)) {
        //accessing to the method which is not available in the other package
        accessProblem = AccessType.PACKAGE_PRIVATE
      }
    }

    if (accessProblem != null) {
      val problem = IllegalMethodAccessProblem(MethodReference.from(definingClass.name, methodNode), accessProblem)
      ctx.registerProblem(problem, getFromMethod())
    }
  }

  /**
   * To resolve an unresolved symbolic reference from D to an interface method in an interface C,
   * the symbolic reference to C given by the interface method reference is first resolved (§5.4.3.1).
   *
   * Therefore, any exception that can be thrown as a result of failure of resolution of an interface
   * reference can be thrown as a result of failure of interface method resolution.
   *
   * If the reference to C can be successfully resolved, exceptions relating to the resolution of the
   * interface method reference itself can be thrown.
   */
  fun resolveInterfaceMethod0(interfaceNode: ClassNode): LookupResult {
    /*
    1) If C is not an interface, interface method resolution throws an IncompatibleClassChangeError.
     */
    if (!VerifierUtil.isInterface(interfaceNode)) {
      ctx.registerProblem(IncompatibleInterfaceToClassChangeProblem(ClassReference(interfaceNode.name)), getFromMethod())
      return FAILED_LOOKUP
    }

    /*
    2) Otherwise, if C declares a method with the name and descriptor specified by
    the interface method reference, method lookup succeeds.
    */
    val matching = (interfaceNode.methods as List<MethodNode>).firstOrNull { it.name == methodName && it.desc == methodDescriptor }
    if (matching != null) {
      return LookupResult(false, ResolvedMethod(interfaceNode, matching))
    }

    /*
    3) Otherwise, if the class Object declares a method with the name and descriptor specified by the
    interface method reference, which has its ACC_PUBLIC flag set and does not have its ACC_STATIC flag set,
    method lookup succeeds.
    */
    val objectClass = VerifierUtil.resolveClassOrProblem(resolver, "java/lang/Object", interfaceNode, ctx, { ProblemLocation.fromClass(interfaceNode.name) }) ?: return FAILED_LOOKUP
    val objectMethod = (objectClass.methods as List<MethodNode>).firstOrNull { it.name == methodName && it.desc == methodDescriptor && VerifierUtil.isPublic(it) && !VerifierUtil.isStatic(it) }
    if (objectMethod != null) {
      return LookupResult(false, ResolvedMethod(objectClass, objectMethod))
    }

    /*
    4) Otherwise, if the maximally-specific superinterface methods (§5.4.3.3) of C for the name
    and descriptor specified by the method reference include exactly one method that does not
    have its ACC_ABSTRACT flag set, then this method is chosen and method lookup succeeds.
     */
    val maximallySpecificSuperInterfaceMethods = getMaximallySpecificSuperInterfaceMethods(interfaceNode) ?: return FAILED_LOOKUP
    val single = maximallySpecificSuperInterfaceMethods.singleOrNull { it.methodNode.name == methodName && it.methodNode.desc == methodDescriptor && !VerifierUtil.isAbstract(it.methodNode) }
    if (single != null) {
      return LookupResult(false, single)
    }

    /*
    5) Otherwise, if any superinterface of C declares a method with the name and descriptor specified by the method
    reference that has neither its ACC_PRIVATE flag nor its ACC_STATIC flag set, one of these is arbitrarily chosen and method lookup succeeds.
     */
    val matchings = getSuperInterfaceMethods(interfaceNode, { it.name == methodName && it.desc == methodDescriptor && !VerifierUtil.isPrivate(it) && !VerifierUtil.isStatic(it) }) ?: return FAILED_LOOKUP
    if (matchings.isNotEmpty()) {
      return LookupResult(false, matchings.first())
    }

    /*
    6) Otherwise, method lookup fails.
     */
    return NOT_FOUND
  }


  /**
   * A maximally-specific superinterface method of a class or interface C for a particular method name
   * and descriptor is any method for which all of the following are true:
   *
   * - The method is declared in a superinterface (direct or indirect) of C.
   * - The method is declared with the specified name and descriptor.
   * - The method has neither its ACC_PRIVATE flag nor its ACC_STATIC flag set.
   * - Where the method is declared in interface I, there exists no other maximally-specific superinterface
   * method of C with the specified name and descriptor that is declared in a subinterface of I.
   */
  private fun getMaximallySpecificSuperInterfaceMethods(start: ClassNode): List<ResolvedMethod>? {
    val predicate: (MethodNode) -> Boolean = { it.name == methodName && it.desc == methodDescriptor && !VerifierUtil.isPrivate(it) && !VerifierUtil.isStatic(it) }
    val allMatching = getSuperInterfaceMethods(start, predicate) ?: return null
    return allMatching.filterIndexed { myIndex, myResolvedMethod ->
      var isDeepest = true
      allMatching.forEachIndexed { otherIndex, otherMethod ->
        if (myIndex != otherIndex && VerifierUtil.isSubclassOf(myResolvedMethod.definingClass, otherMethod.definingClass, resolver, ctx)) {
          isDeepest = false
        }
      }
      isDeepest
    }
  }

  /**
   * @return all direct and indirect super-interface methods matching the given predicate
   */
  private fun getSuperInterfaceMethods(start: ClassNode, predicate: (MethodNode) -> Boolean): List<ResolvedMethod>? {
    //breadth-first-search
    val queue: Queue<ClassNode> = LinkedList<ClassNode>()
    val visited = hashSetOf<String>()
    val result = arrayListOf<ResolvedMethod>()
    queue.add(start)
    visited.add(start.name)
    while (!queue.isEmpty()) {
      val cur = queue.remove()
      val matching = (cur.methods as List<MethodNode>).filter(predicate)
      result.addAll(matching.map { ResolvedMethod(cur, it) })

      (cur.interfaces as List<String>).forEach {
        if (it !in visited) {
          val resolveClass = VerifierUtil.resolveClassOrProblem(resolver, it, cur, ctx, { ProblemLocation.fromClass(cur.name) }) ?: return null
          visited.add(it)
          queue.add(resolveClass)
        }
      }

      val superName = cur.superName
      if (superName != null) {
        if (superName !in visited) {
          val resolvedSuper = VerifierUtil.resolveClassOrProblem(resolver, superName, cur, ctx, { ProblemLocation.fromClass(cur.name) }) ?: return null
          visited.add(superName)
          queue.add(resolvedSuper)
        }
      }
    }
    return result
  }

  /**
   * @return true if success, false otherwise
   */
  private fun dfs0(currentClass: ClassNode, visited: MutableSet<String>): Boolean {
    visited.add(currentClass.name)
    (currentClass.interfaces as List<String>).forEach {
      if (it !in visited) {
        val resolveClass = VerifierUtil.resolveClassOrProblem(resolver, it, currentClass, ctx, { ProblemLocation.fromClass(currentClass.name) })
        if (resolveClass == null || !dfs0(resolveClass, visited)) {
          return false
        }
      }
    }
    return true
  }

  /**
   * To resolve an unresolved symbolic reference from D to a method in a class C,
   * the symbolic reference to C given by the method reference is first resolved (§5.4.3.1).
   *
   * Therefore, any exception that can be thrown as a result of failure of resolution of
   * a class reference can be thrown as a result of failure of method resolution.
   *
   * If the reference to C can be successfully resolved, exceptions relating to the resolution of the method reference itself can be thrown.
   */
  fun resolveClassMethod0(classNode: ClassNode): LookupResult {
    /*
      1) If C is an interface, method resolution throws an IncompatibleClassChangeError.
    */
    if (VerifierUtil.isInterface(classNode)) {
      ctx.registerProblem(IncompatibleClassToInterfaceChangeProblem(ClassReference(classNode.name)), getFromMethod())
      return FAILED_LOOKUP
    }

    /*
      2) Otherwise, method resolution attempts to locate the referenced method in C and its superclasses:
    */
    val (shouldStop2, resolvedMethod2) = resolveClassMethodStep2(classNode)
    if (shouldStop2) {
      return FAILED_LOOKUP
    }
    if (resolvedMethod2 != null) {
      return LookupResult(false, resolvedMethod2)
    }

    /*
      3) Otherwise, method resolution attempts to locate the referenced method in the superinterfaces of the specified class C:
    */
    val (shouldStop3, resolvedMethod3) = resolveClassMethodStep3(classNode)
    if (shouldStop3) {
      return FAILED_LOOKUP
    }
    if (resolvedMethod3 != null) {
      return LookupResult(false, resolvedMethod3)
    }

    return NOT_FOUND
  }

  data class LookupResult(val fail: Boolean, val resolvedMethod: ResolvedMethod?)

  companion object {
    val FAILED_LOOKUP = LookupResult(true, null)
    val NOT_FOUND = LookupResult(false, null)
  }

  fun resolveClassMethodStep2(currentClass: ClassNode): LookupResult {
    /*
    2.1) If C declares exactly one method with the name specified by the method reference,
      and the declaration is a signature polymorphic method (§2.9), then method lookup succeeds.

      All the class names mentioned in the descriptor are resolved (§5.4.3.1).

      The resolved method is the signature polymorphic method declaration. It is not necessary for C to declare
      a method with the descriptor specified by the method reference.
    */
    val methods = currentClass.methods as List<MethodNode>

    val matchByName = methods.firstOrNull() { it.name == methodName }
    if (matchByName != null && VerifierUtil.isSignaturePolymorphic(currentClass.name, matchByName) && methods.count { it.name == methodName } == 1) {
      return LookupResult(false, ResolvedMethod(currentClass, matchByName))
    }

    /*
    2.2) Otherwise, if C declares a method with the name and descriptor
    specified by the method reference, method lookup succeeds.
     */
    val matching = methods.find { methodName == it.name && methodDescriptor == it.desc }
    if (matching != null) {
      return LookupResult(false, ResolvedMethod(currentClass, matching))
    }

    /*
    2.3) Otherwise, if C has a superclass, step 2 of method resolution is recursively invoked
    on the direct superclass of C.
     */
    val superName = currentClass.superName
    if (superName != null) {
      val resolvedSuper = VerifierUtil.resolveClassOrProblem(resolver, superName, currentClass, ctx, { ProblemLocation.fromClass(currentClass.name) }) ?: return LookupResult(true, null)
      val (shouldStopLookup, resolvedMethod) = resolveClassMethodStep2(resolvedSuper)
      if (shouldStopLookup) {
        return FAILED_LOOKUP
      }
      return LookupResult(false, resolvedMethod)
    }

    return NOT_FOUND
  }

  fun resolveClassMethodStep3(currentClass: ClassNode): LookupResult {

    /*
      3.1) If the maximally-specific superinterface methods of C for the name and descriptor specified
    by the method reference include exactly one method that does not have its ACC_ABSTRACT
    flag set, then this method is chosen and method lookup succeeds.
     */
    val maximallySpecificSuperInterfaceMethods = getMaximallySpecificSuperInterfaceMethods(currentClass) ?: return FAILED_LOOKUP
    val single = maximallySpecificSuperInterfaceMethods.singleOrNull { it.methodNode.name == methodName && it.methodNode.desc == methodDescriptor && !VerifierUtil.isAbstract(it.methodNode) }
    if (single != null) {
      return LookupResult(false, single)
    }

    /*
      3.2) Otherwise, if any superinterface of C declares a method with the name and descriptor specified
    by the method reference that has neither its ACC_PRIVATE flag nor its ACC_STATIC
    flag set, one of these is arbitrarily chosen and method lookup succeeds.
     */
    val matchings = getSuperInterfaceMethods(currentClass, { it.name == methodName && it.desc == methodDescriptor && !VerifierUtil.isPrivate(it) && !VerifierUtil.isStatic(it) }) ?: return FAILED_LOOKUP
    if (matchings.isNotEmpty()) {
      return LookupResult(false, matchings.first())
    }

    /*
    3.3) Otherwise, method lookup fails.
     */
    return NOT_FOUND
  }

  data class ResolvedMethod(val definingClass: ClassNode, val methodNode: MethodNode)

}
