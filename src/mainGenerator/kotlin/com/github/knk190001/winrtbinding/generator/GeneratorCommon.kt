package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.INamedEntity
import com.github.knk190001.winrtbinding.generator.model.entities.SparseStruct
import com.github.knk190001.winrtbinding.generator.model.entities.SparseTypeReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Native
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.ByReference
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import kotlin.reflect.KClass

internal fun TypeSpec.Builder.generateByReferenceType(entity: INamedEntity) {
    val className = ClassName("", entity.name)

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

internal fun TypeSpec.Builder.addByReferenceType(entity: INamedEntity) {
    val byReference = TypeSpec.classBuilder("ByReference").apply {
        generateByReferenceType(entity)
    }.build()
    addType(byReference)
}

fun SparseTypeReference.asClassName(structByValue: Boolean = true): TypeName {
    if (isArray) {
        val baseClass = if (isReference) {
            ClassName("com.github.knk190001.winrtbinding", "OutArray")
        } else {
            Array::class.asClassName()
        }
        return baseClass
            .parameterizedBy(copy(isArray = false, isReference = false).asClassName())
    }

    if (namespace == "System") {
        return when (name) {
            "UInt32" -> WinDef.UINT::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Int32" -> Int::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "String" -> String::class.asClassName()
            "UInt32&" -> WinDef.UINTByReference::class.asClassName()
            "Object" -> ClassName("com.sun.jna.platform.win32.COM", "Unknown")
            "Int64" -> Long::class.asClassName()
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (this.isReference) {
        if (genericParameters != null) {
            val name = getProjectedName()
            return ClassName(name, "ByReference")
        }
        return ClassName(fullName(), "ByReference")
    }
    if (genericParameters != null) {
        val name = getProjectedName()
        return ClassName(this.namespace, name)
    }

    if (lookUpTypeReference(this) is SparseStruct && structByValue) {
        return ClassName(namespace, name).nestedClass("ByValue")
    }
    return ClassName(this.namespace, this.name)
}

fun SparseTypeReference.asKClass(): KClass<*> {
    if (isArray) return Nothing::class
    if (namespace == "System") {
        return when (name) {
            "UInt32" -> WinDef.UINT::class
            "Double" -> Double::class
            "Boolean" -> Boolean::class
            "Int32" -> Int::class
            "Void" -> Unit::class
            "String" -> String::class
            "UInt32&" -> WinDef.UINTByReference::class
            "Object" -> Unknown::class
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    return Nothing::class
}

fun SparseTypeReference.byReferenceClassName(): TypeName {
    if (isArray) {
        return ClassName("com.github.knk190001.winrtbinding", "OutArray")
            .parameterizedBy(copy(isReference = false, isArray = false).asClassName())

    }
    if (namespace == "System") {
        return when (name) {
            "UInt32" -> WinDef.UINTByReference::class.asClassName()
            "Double" -> DoubleByReference::class.asClassName()
            "Boolean" -> ByteByReference::class.asClassName()
            "Int32" -> IntByReference::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "String" -> WinNT.HANDLEByReference::class.asClassName()
            "Object" -> ClassName("com.sun.jna.platform.win32.COM.Unknown", "ByReference")
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (genericParameters != null) {
        val name = getProjectedName()
        return ClassName("${this.namespace}.$name", "ByReference")
    }

    return ClassName(this.namespace + ".${this.name}", "ByReference")
}

