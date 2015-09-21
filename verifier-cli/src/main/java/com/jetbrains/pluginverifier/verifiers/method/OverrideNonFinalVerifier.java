package com.jetbrains.pluginverifier.verifiers.method;

import com.intellij.structure.pool.ResolverUtil;
import com.intellij.structure.resolvers.Resolver;
import com.jetbrains.pluginverifier.VerificationContext;
import com.jetbrains.pluginverifier.problems.OverridingFinalMethodProblem;
import com.jetbrains.pluginverifier.problems.ProblemLocation;
import com.jetbrains.pluginverifier.verifiers.util.VerifierUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * @author Dennis.Ushakov
 */
public class OverrideNonFinalVerifier implements MethodVerifier {
  public void verify(final ClassNode clazz, final MethodNode method, final Resolver resolver, final VerificationContext ctx) {
    if ((method.access & Opcodes.ACC_PRIVATE) != 0) return;
    final String superClass = clazz.superName;
    final ResolverUtil.MethodLocation superMethod = ResolverUtil.findMethod(resolver, superClass, method.name, method.desc);
    if (superMethod == null) return;
    if (VerifierUtil.isFinal(superMethod.getMethodNode()) && !VerifierUtil.isAbstract(superMethod.getMethodNode())) {
      ctx.registerProblem(new OverridingFinalMethodProblem(superMethod.getMethodDescr()), new ProblemLocation(clazz.name, method.name + method.desc));
    }
  }
}
