package com.github.knk190001.winrtbinding.generator

import com.squareup.kotlinpoet.CodeBlock

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
}

class StringMarshalGenerator: IMarshalGenerator {
    override fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        val cb = CodeBlock.builder()
        cb.apply {
            addStatement("val ${varName}_Native = $varName.toHandle()")
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
    override fun generateToNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        return varName to CodeBlock.of("")
    }

    override fun generateFromNativeMarshalCode(varName: String): Pair<String, CodeBlock> {
        return varName to CodeBlock.of("")
    }

}
