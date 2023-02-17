package com.github.knk190001.winrtbinding.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.FromNativeContext
import com.sun.jna.NativeMapped
import com.sun.jna.ptr.ByReference
import com.github.knk190001.winrtbinding.generator.model.entities.SparseEnum

fun generateEnum(sEnum : SparseEnum): FileSpec {
    val fileSpec = FileSpec.builder(sEnum.namespace, sEnum.name)
    val type = TypeSpec.enumBuilder(sEnum.name).apply {
        addSuperinterface(NativeMapped::class)
        primaryConstructor(
            FunSpec.constructorBuilder().apply {
                addParameter("value",Int::class)
            }.build()
        )

        sEnum.values.entries.map {
            it.key to TypeSpec.anonymousClassBuilder()
                .addSuperclassConstructorParameter(it.value.toString())
                .build()
        }.forEach {
            addEnumConstant(it.first,it.second)
        }

        val fromNativeSpec = FunSpec.builder("fromNative").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("nativeValue", Any::class.asTypeName().copy(true))
            addParameter("context", FromNativeContext::class.asTypeName().copy(true))
            returns(ClassName(sEnum.namespace, sEnum.name))

            val cb = CodeBlock.builder().apply {
                addStatement("if (nativeValue !is Int) throw %T()", IllegalArgumentException::class)
                beginControlFlow("return when(nativeValue) ")
                sEnum.values.entries.forEach {
                    addStatement("${it.value} -> ${it.key}")
                }
                addStatement("else -> throw %T()", IllegalArgumentException::class)
                endControlFlow()
            }.build()

            addCode(cb)
        }.build()

        val toNativeSpec = FunSpec.builder("toNative").apply {
            addModifiers(KModifier.OVERRIDE)
            returns(Int::class)
            addCode(CodeBlock.of("return this.value"))
        }.build()



        val nativeTypeSpec = FunSpec.builder("nativeType").apply {
            addModifiers(KModifier.OVERRIDE)
            returns(Class::class.asClassName().parameterizedBy(STAR))
            addCode("return %T::class.java",Integer::class.java)
        }.build()

        val byRefSpec = TypeSpec.classBuilder("ByReference").apply {
            superclass(ByReference::class)
            addSuperclassConstructorParameter("4")
            val setValueSpec = FunSpec.builder("setValue").apply {
                addParameter("newValue",ClassName(sEnum.namespace,sEnum.name))
                addCode("pointer.setInt(0, newValue.value)")
            }.build()

            val getValueSpec = FunSpec.builder("getValue").apply {
                returns(ClassName(sEnum.namespace,sEnum.name))
                addCode("return ${sEnum.name}.values()[0].fromNative(this.pointer.getInt(0), null)")
            }.build()

            addFunction(setValueSpec)
            addFunction(getValueSpec)
        }.build()

        addFunction(fromNativeSpec)
        addFunction(toNativeSpec)
        addFunction(nativeTypeSpec)
        addProperty(PropertySpec.builder("value",Int::class).initializer("value").build())
        addType(byRefSpec)
    }.build()
    fileSpec.addType(type)
    return fileSpec.build()
}