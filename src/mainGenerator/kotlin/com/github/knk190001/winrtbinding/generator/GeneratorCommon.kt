package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.SparseInterface
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.TypeSpec
import com.sun.jna.Native
import com.sun.jna.ptr.ByReference

internal fun TypeSpec.Builder.generateByReferenceType(sparseInterface: SparseInterface) {
    val className = ClassName("", sparseInterface.name)

    superclass(ByReference::class)
    val ptrSize = Native::class.member("POINTER_SIZE")
    addSuperclassConstructorParameter("%M", ptrSize)

    val getValueSpec = FunSpec.builder("getValue").apply {
        addCode("return %T(pointer.getPointer(0))", className)
        returns(className)
    }.build()
    addFunction(getValueSpec)

    val setValueSpec = FunSpec.builder("setValue").apply {
        addParameter("value", className)
        addCode("pointer.setPointer(0, value.pointer)")
    }.build()
    addFunction(setValueSpec)
}
