package com.jetbrains.pluginverifier.verifiers;

import com.intellij.structure.domain.IdeaPlugin;
import com.intellij.structure.pool.ClassPool;
import com.intellij.structure.resolvers.CacheResolver;
import com.intellij.structure.resolvers.Resolver;
import com.jetbrains.pluginverifier.VerificationContext;
import com.jetbrains.pluginverifier.Verifier;
import com.jetbrains.pluginverifier.problems.FailedToReadClassProblem;
import com.jetbrains.pluginverifier.problems.ProblemLocation;
import com.jetbrains.pluginverifier.utils.DependenciesCache;
import com.jetbrains.pluginverifier.verifiers.clazz.ClassVerifier;
import com.jetbrains.pluginverifier.verifiers.field.FieldVerifier;
import com.jetbrains.pluginverifier.verifiers.instruction.InstructionVerifier;
import com.jetbrains.pluginverifier.verifiers.method.MethodVerifier;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author Dennis.Ushakov
 */
public class ReferencesVerifier implements Verifier {

  @Override
  public void verify(@NotNull IdeaPlugin plugin, @NotNull VerificationContext ctx) {
    final ClassPool pluginPool = plugin.getPluginClassPool();

    Resolver cacheResolver = new CacheResolver(DependenciesCache.getInstance().getResolver(ctx.getIde(), plugin));

    final Collection<String> classes = pluginPool.getAllClasses();
    for (String className : classes) {
      final ClassNode node = pluginPool.findClass(className);

      if (node == null) {
        ctx.registerProblem(FailedToReadClassProblem.INSTANCE, new ProblemLocation(className));
        continue;
      }

      verifyClass(cacheResolver, node, ctx);
    }
  }

  private void verifyClass(@NotNull Resolver resolver, @NotNull ClassNode node, @NotNull VerificationContext ctx) {
    for (ClassVerifier verifier : Verifiers.getClassVerifiers()) {
      verifier.verify(node, resolver, ctx);
    }

    for (MethodNode method : (List<MethodNode>)node.methods) {
      for (MethodVerifier verifier : Verifiers.getMemberVerifiers()) {
        verifier.verify(node, method, resolver, ctx);
      }

      final InsnList instructions = method.instructions;
      for (Iterator<AbstractInsnNode> i = instructions.iterator(); i.hasNext(); ) {
        AbstractInsnNode instruction = i.next();
        for (InstructionVerifier verifier : Verifiers.getInstructionVerifiers()) {
          verifier.verify(node, method, instruction, resolver, ctx);
        }
      }
    }

    for (FieldNode method : (List<FieldNode>)node.fields) {
      for (FieldVerifier verifier : Verifiers.getFieldVerifiers()) {
        verifier.verify(node, method, resolver, ctx);
      }
    }
  }
}
