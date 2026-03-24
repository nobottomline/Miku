package miku.extension.runner

import org.objectweb.asm.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/**
 * Patches converted extension JARs for Kotlin version compatibility.
 *
 * Extensions compiled with Kotlin 1.x use inline class helper methods
 * (e.g. Result.constructor_impl, Result.isFailure_impl) that were
 * removed/changed in Kotlin 2.x. This patcher injects a compatibility
 * class into the JAR with the missing bridge methods.
 */
object KotlinCompatPatcher {
    private val logger = LoggerFactory.getLogger(KotlinCompatPatcher::class.java)

    /**
     * Inject kotlin.Result compat class into a JAR if it doesn't already have one.
     */
    fun patchJar(jarFile: File): File {
        val tempFile = Files.createTempFile("miku-patched-", ".jar").toFile()

        try {
            JarFile(jarFile).use { inputJar ->
                JarOutputStream(FileOutputStream(tempFile)).use { outputJar ->
                    // Copy all existing entries
                    val entries = inputJar.entries()
                    val existingEntries = mutableSetOf<String>()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        existingEntries.add(entry.name)
                        outputJar.putNextEntry(JarEntry(entry.name))
                        inputJar.getInputStream(entry).copyTo(outputJar)
                        outputJar.closeEntry()
                    }

                    // Inject kotlin compat classes if not present
                    if ("kotlin/Result.class" !in existingEntries) {
                        injectResultCompat(outputJar)
                    }
                    if ("kotlin/ResultKt.class" !in existingEntries) {
                        injectResultKtCompat(outputJar)
                    }
                    if ("kotlin/time/Duration.class" !in existingEntries) {
                        injectDurationCompat(outputJar)
                    }
                    if ("kotlin/UInt.class" !in existingEntries) {
                        injectUIntCompat(outputJar)
                    }
                    if ("kotlin/ULong.class" !in existingEntries) {
                        injectULongCompat(outputJar)
                    }
                }
            }

            // Replace original
            Files.move(tempFile.toPath(), jarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            logger.debug("Patched JAR: ${jarFile.name}")
            return jarFile
        } catch (e: Exception) {
            tempFile.delete()
            logger.warn("Failed to patch JAR: ${jarFile.name}, continuing without patch", e)
            return jarFile
        }
    }

    /**
     * Generate kotlin/Result.class with the Kotlin 1.x compatibility methods:
     * - constructor_impl(Object): Object (identity - just returns the value)
     * - isFailure_impl(Object): boolean (checks if value is a Result$Failure)
     * - exceptionOrNull_impl(Object): Throwable? (extracts exception from failure)
     * - box_impl(Object): Result (creates boxed Result)
     */
    private fun injectResultCompat(jar: JarOutputStream) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/Result", null, "java/lang/Object", null)

        // Field: value
        cw.visitField(Opcodes.ACC_PRIVATE + Opcodes.ACC_FINAL, "value", "Ljava/lang/Object;", null, null)

        // Constructor(Object)
        var mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/Object;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitFieldInsn(Opcodes.PUTFIELD, "kotlin/Result", "value", "Ljava/lang/Object;")
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        // static constructor_impl(Object): Object — identity function
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "constructor_impl", "(Ljava/lang/Object;)Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // static isFailure_impl(Object): boolean
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "isFailure_impl", "(Ljava/lang/Object;)Z", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitTypeInsn(Opcodes.INSTANCEOF, "kotlin/Result\$Failure")
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // static exceptionOrNull_impl(Object): Throwable
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "exceptionOrNull_impl", "(Ljava/lang/Object;)Ljava/lang/Throwable;", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitTypeInsn(Opcodes.INSTANCEOF, "kotlin/Result\$Failure")
        val notFailure = Label()
        mv.visitJumpInsn(Opcodes.IFEQ, notFailure)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitTypeInsn(Opcodes.CHECKCAST, "kotlin/Result\$Failure")
        mv.visitFieldInsn(Opcodes.GETFIELD, "kotlin/Result\$Failure", "exception", "Ljava/lang/Throwable;")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitLabel(notFailure)
        mv.visitInsn(Opcodes.ACONST_NULL)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // static box_impl(Object): Result
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, "box_impl", "(Ljava/lang/Object;)Lkotlin/Result;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "kotlin/Result")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "kotlin/Result", "<init>", "(Ljava/lang/Object;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()

        // unbox_impl(): Object
        mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "unbox_impl", "()Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitFieldInsn(Opcodes.GETFIELD, "kotlin/Result", "value", "Ljava/lang/Object;")
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // Companion field
        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, "Companion", "Lkotlin/Result\$Companion;", null, null)

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/Result.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()

        // Also generate Result$Failure inner class
        injectResultFailure(jar)
        // And Result$Companion
        injectResultCompanion(jar)
    }

    private fun injectResultFailure(jar: JarOutputStream) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/Result\$Failure", null, "java/lang/Object", null)

        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "exception", "Ljava/lang/Throwable;", null, null)

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Throwable;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitFieldInsn(Opcodes.PUTFIELD, "kotlin/Result\$Failure", "exception", "Ljava/lang/Throwable;")
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/Result\$Failure.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    private fun injectResultCompanion(jar: JarOutputStream) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/Result\$Companion", null, "java/lang/Object", null)

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/Result\$Companion.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    /**
     * Generate kotlin/ResultKt.class with:
     * - createFailure(Throwable): Object
     */
    private fun injectResultKtCompat(jar: JarOutputStream) {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/ResultKt", null, "java/lang/Object", null)

        // static createFailure(Throwable): Object
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "createFailure", "(Ljava/lang/Throwable;)Ljava/lang/Object;", null, null)
        mv.visitCode()
        mv.visitTypeInsn(Opcodes.NEW, "kotlin/Result\$Failure")
        mv.visitInsn(Opcodes.DUP)
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "kotlin/Result\$Failure", "<init>", "(Ljava/lang/Throwable;)V", false)
        mv.visitInsn(Opcodes.ARETURN)
        mv.visitMaxs(3, 1)
        mv.visitEnd()

        // static throwOnFailure(Object): void
        val mv2 = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "throwOnFailure", "(Ljava/lang/Object;)V", null, null)
        mv2.visitCode()
        mv2.visitVarInsn(Opcodes.ALOAD, 0)
        mv2.visitTypeInsn(Opcodes.INSTANCEOF, "kotlin/Result\$Failure")
        val notFailure = Label()
        mv2.visitJumpInsn(Opcodes.IFEQ, notFailure)
        mv2.visitVarInsn(Opcodes.ALOAD, 0)
        mv2.visitTypeInsn(Opcodes.CHECKCAST, "kotlin/Result\$Failure")
        mv2.visitFieldInsn(Opcodes.GETFIELD, "kotlin/Result\$Failure", "exception", "Ljava/lang/Throwable;")
        mv2.visitInsn(Opcodes.ATHROW)
        mv2.visitLabel(notFailure)
        mv2.visitInsn(Opcodes.RETURN)
        mv2.visitMaxs(1, 1)
        mv2.visitEnd()

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/ResultKt.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    /**
     * Inject kotlin/time/Duration compat with constructor_impl (identity).
     */
    private fun injectDurationCompat(jar: JarOutputStream) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/time/Duration", null, "java/lang/Object", null)

        // static constructor_impl(long): long — identity for inline class
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "constructor_impl", "(J)J", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.LLOAD, 0)
        mv.visitInsn(Opcodes.LRETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        // Companion field
        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL, "Companion", "Lkotlin/time/Duration\$Companion;", null, null)
        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/time/Duration.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    private fun injectUIntCompat(jar: JarOutputStream) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/UInt", null, "java/lang/Object", null)

        // static constructor_impl(int): int
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "constructor_impl", "(I)I", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/UInt.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }

    private fun injectULongCompat(jar: JarOutputStream) {
        val cw = ClassWriter(0)
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC + Opcodes.ACC_FINAL, "kotlin/ULong", null, "java/lang/Object", null)

        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, "constructor_impl", "(J)J", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.LLOAD, 0)
        mv.visitInsn(Opcodes.LRETURN)
        mv.visitMaxs(2, 2)
        mv.visitEnd()

        cw.visitEnd()

        jar.putNextEntry(JarEntry("kotlin/ULong.class"))
        jar.write(cw.toByteArray())
        jar.closeEntry()
    }
}
