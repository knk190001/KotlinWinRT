@file:Suppress("DuplicatedCode")

package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.Marshaller.marshalVariablesFromNative
import com.github.knk190001.winrtbinding.generator.model.entities.SparseClass
import com.github.knk190001.winrtbinding.generator.model.entities.SparseDelegate
import com.github.knk190001.winrtbinding.generator.model.entities.SparseInterface
import com.github.knk190001.winrtbinding.runtime.DelegateFactory
import com.github.knk190001.winrtbinding.runtime.interfaces.NativeDelegateFactory
import com.squareup.kotlinpoet.*
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import kotlin.reflect.KType

fun generateClosedDelegate(sparseDelegate: SparseDelegate): FileSpec {
    return FileSpec.builder(sparseDelegate.namespace, sparseDelegate.name).apply {
        addImports()
        val nativeInterface = TypeSpec.funInterfaceBuilder(sparseDelegate.name).apply {
            addSuperinterface(StdCallLibrary.StdCallCallback::class)
            addInterfaceInvokeMethod(sparseDelegate)
            addNativeFactoryClass(sparseDelegate)
        }.build()
        addType(nativeInterface)
    }.build()
}

private fun FileSpec.Builder.addImports() {
    addImport("kotlin.reflect", "typeOf")
}

private fun TypeSpec.Builder.addNativeFactoryClass(sparseDelegate: SparseDelegate) {
    val nativeFactory = TypeSpec.objectBuilder("NativeFactory").apply {
        addSuperinterface(NativeDelegateFactory::class)
        addFactoryCreate(sparseDelegate)
        addStaticInitializer(sparseDelegate)
    }.build()
    addType(nativeFactory)
}

private fun TypeSpec.Builder.addStaticInitializer(sparseDelegate: SparseDelegate) {
    val cb = CodeBlock.builder().apply {
        addStatement(
            "%T.register(typeOf<%T>(), NativeFactory)",
            DelegateFactory::class,
            sparseDelegate.withName(sparseDelegate.name.replaceAfter('_', "").dropLast(1))
                .asTypeReference().asGenericTypeParameter())
    }.build()
    addInitializerBlock(cb)
}

private fun TypeSpec.Builder.addFactoryCreate(sparseDelegate: SparseDelegate) {
    val createSpec = FunSpec.builder("create").apply {
        addModifiers(KModifier.OVERRIDE)
        addParameter("type", KType::class)
        addParameter("body", Any::class)
        returns(StdCallLibrary.StdCallCallback::class)

        val cb = CodeBlock.builder().apply {
            val delegateWithoutSuffix =
                sparseDelegate.withName(sparseDelegate.name.replaceAfter('_', "").dropLast(1))

            beginControlFlow("if (body::class != %T::class)", delegateWithoutSuffix.asTypeReference().asClassName())
            addStatement(
                "throw %T(%S)",
                IllegalArgumentException::class,
                "fn is not of type ${delegateWithoutSuffix.name}"
            )
            endControlFlow()

            val genericDelegate = delegateWithoutSuffix.asTypeReference().asGenericTypeParameter()
            addStatement("val casted = body as %T", genericDelegate)

            beginControlFlow("if (casted.type != typeOf<%T>())", genericDelegate)
            addStatement(
                "throw %T(%S)",
                IllegalArgumentException::class,
                "fn is not of type ${delegateWithoutSuffix.name}"
            )
            endControlFlow()

            beginControlFlow("return$nbsp%T", sparseDelegate.asTypeReference().asClassName())
            sparseDelegate.parameters
                .joinToString(prefix = "thisPtr, ", postfix = " ->") { it.name }
                .let(::addStatement)
            addStatement("val thisObj = %T(typeOf<%T>(), thisPtr)", genericDelegate, genericDelegate)

            addStatement("")
            val marshalledVariables = marshalVariablesFromNative(sparseDelegate.parameters, this)
            addStatement("")

            if (sparseDelegate.returnType.name != "Void") {
                add("val result = ")
            }
            addStatement("casted.body!!(thisObj, ${marshalledVariables.joinToString()})")
            if (sparseDelegate.returnType.name != "Void") {
                addStatement("retVal.setValue(result)")
            }
            addStatement("%T(0)", WinNT.HRESULT::class)
            endControlFlow()
        }.build()
        addCode(cb)
    }.build()
    addFunction(createSpec)
}

private fun TypeSpec.Builder.addInterfaceInvokeMethod(sparseDelegate: SparseDelegate) {
    val invoke = FunSpec.builder("invoke").apply {
        addParameter("thisPtr", Pointer::class)
        addModifiers(KModifier.ABSTRACT)
        sparseDelegate.parameters.forEach {
            if (Marshaller.marshalsEx.contains(it.type.asKClass())) {
                addParameter(it.name, Marshaller.marshalsEx[it.type.asKClass()]!!.nativeType)
            } else {
                addParameter(
                    it.name,
                    it.type.asClassName(false).copy(!it.type.isPrimitiveSystemType() && !it.type.isArray)
                )
            }
        }
        if (sparseDelegate.returnType.name != "Void") {
            addParameter("retVal", sparseDelegate.returnType.byReferenceClassName())
        }
        returns(WinNT.HRESULT::class)
    }.build()
    addFunction(invoke)
}