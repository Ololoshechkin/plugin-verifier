package com.jetbrains.plugin.structure.classes.resolvers

import com.google.common.collect.Iterators
import com.jetbrains.plugin.structure.classes.utils.AsmUtil
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.*
import java.util.zip.ZipFile

class JarFileResolver(private val originalFile: File) : Resolver() {

  private val jarFile: ZipFile = ZipFile(originalFile)

  private val classes: Set<String> = readClasses()

  private fun readClasses(): Set<String> {
    val entries = jarFile.entries()
    val classes = HashSet<String>()
    while (entries.hasMoreElements()) {
      val entry = entries.nextElement()
      val entryName = entry.name
      if (entryName.endsWith(CLASS_SUFFIX)) {
        classes.add(entryName.substringBeforeLast(CLASS_SUFFIX))
      }
    }
    return classes
  }

  override fun getAllClasses(): Iterator<String> = Iterators.unmodifiableIterator<String>(classes.iterator())

  override fun toString(): String = jarFile.name

  override fun isEmpty(): Boolean = classes.isEmpty()

  override fun containsClass(className: String): Boolean = classes.contains(className)

  override fun getClassPath(): List<File> = listOf(originalFile)

  override fun getFinalResolvers(): List<Resolver> = listOf(this as Resolver)

  override fun findClass(className: String): ClassNode? =
      if (classes.contains(className)) evaluateNode(className) else null

  private fun evaluateNode(className: String): ClassNode? {
    val entry = jarFile.getEntry(className + CLASS_SUFFIX) ?: return null
    jarFile.getInputStream(entry).use { inputStream ->
      return AsmUtil.readClassNode(className, inputStream)
    }
  }

  override fun getClassLocation(className: String): Resolver? = if (containsClass(className)) this else null

  override fun close() {
    jarFile.close()
  }

  companion object {
    private val CLASS_SUFFIX = ".class"
  }
}