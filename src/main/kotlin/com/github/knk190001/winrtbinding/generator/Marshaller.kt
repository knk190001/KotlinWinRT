package com.github.knk190001.winrtbinding.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.asClassName
import com.sun.jna.platform.win32.WinNT.HANDLE
import kotlin.reflect.KClass

object Marshaller {
    val default: IMarshalGenerator = IdentityMarshalGenerator()

    val marshals = mapOf(
        String::class to StringMarshalGenerator(),
        Boolean::class to BooleanMarshalGenerator()
    )
}

interface IMarshalGenerator {
    fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock>
    fun generateFromNativeMarshalCode(varName: String): Pair<String, CodeBlock>

    val nativeType: ClassName
    val managedType: KClass<*>
}

class StringMarshalGenerator: IMarshalGenerator {
    override val nativeType = HANDLE::class.asClassName()
    override val managedType = String::class

    override fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        val cb = CodeBlock.builder()
        cb.apply {
            addStatement("val ${varName}_Native = $varName!!.toHandle()")
        }
        return "${varName}_Native" to cb.build()
    }

    override fun generateFromNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        val cb = CodeBlock.builder()
        cb.apply {
            addStatement("val ${varName}_Managed = $varName.handleToString()")
        }
        return "${varName}_Managed" to cb.build()
    }
}

class BooleanMarshalGenerator: IMarshalGenerator {
    override val nativeType = Byte::class.asClassName()
    override val managedType = Boolean::class
    override fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        val cb = CodeBlock.builder()
        cb.apply {
            addStatement("val ${varName}_Native: Byte = if($varName) 1 else 0 ")
        }
        return "${varName}_Native" to cb.build()
    }

    override fun generateFromNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        val cb = CodeBlock.builder()
        cb.apply {
            addStatement("val ${varName}_Managed = $varName != 0.toByte()")
        }
        return "${varName}_Managed" to cb.build()
    }

}

class IdentityMarshalGenerator : IMarshalGenerator {
    override val managedType = Any::class
    override val nativeType = Any::class.asClassName()

    override fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        return varName to CodeBlock.of("")
    }

    override fun generateFromNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        return varName to CodeBlock.of("")
    }

}
