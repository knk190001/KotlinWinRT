package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.SparseDelegate
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

fun generateDelegate(sparseEntity: SparseDelegate) = FileSpec.builder(sparseEntity.namespace, sparseEntity.name).apply {
    val funInterface = TypeSpec.funInterfaceBuilder(sparseEntity.name).apply {
        addSuperinterface(StdCallCallback::class)
        val invoke = FunSpec.builder("invoke").addModifiers(KModifier.ABSTRACT).build()
        addFunction(invoke)
    }.build()
    addType(funInterface)
}.build()
