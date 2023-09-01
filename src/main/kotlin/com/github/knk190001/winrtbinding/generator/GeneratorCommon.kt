package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.github.knk190001.winrtbinding.runtime.CharByReference
import com.github.knk190001.winrtbinding.runtime.IByReference
import com.github.knk190001.winrtbinding.runtime.Signature
import com.github.knk190001.winrtbinding.runtime.WinRTByReference
import com.github.knk190001.winrtbinding.runtime.interfaces.IUnknown
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Native
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.CHARByReference
import com.sun.jna.platform.win32.WinDef.ULONG
import com.sun.jna.platform.win32.WinDef.USHORT
import com.sun.jna.platform.win32.WinDef.USHORTByReference
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.ptr.*
import kotlin.reflect.KClass

internal fun TypeSpec.Builder.generateByReferenceType(
    entity: INamedEntity,
    genericParams: List<SparseGenericParameter> = emptyList()
) {
    val className = ClassName.bestGuess("${entity.namespace}.${entity.name}")

    superclass(ByReference::class)
    val ptrSize = Native::class.member("POINTER_SIZE")
    addSuperclassConstructorParameter("%M", ptrSize)

    val getValueSpec = FunSpec.builder("getValue").apply {
        addModifiers(KModifier.OVERRIDE)
        addCode("return %T(pointer.getPointer(0))", className)
    }.build()
    addFunction(getValueSpec)

    val setValueSpec = FunSpec.builder("setValue").apply {
        addParameter("value", className)
        addCode("pointer.setPointer(0, value.pointer)")
    }.build()
    addFunction(setValueSpec)
}

internal fun TypeSpec.Builder.addByReferenceType(entity: INamedEntity) {
    val brAnnotationSpec = AnnotationSpec.builder(WinRTByReference::class)
        .addMember("brClass = %L.ByReference::class", entity.name)
        .build()
    addAnnotation(brAnnotationSpec)
    val byReference = TypeSpec.classBuilder("ByReference").apply {
        addSuperinterface(IByReference::class.asClassName().parameterizedBy(ClassName("",entity.name)))
        generateByReferenceType(entity)
    }.build()
    addType(byReference)
}

fun SparseTypeReference.asClassName(structByValue: Boolean = true, nullable: Boolean = false): TypeName {
    if (isArray) {
        val baseClass = if (isReference) {
            ClassName("com.github.knk190001.winrtbinding.runtime", "OutArray")
        } else {
            Array::class.asClassName()
        }
        val nonArrayCopy = copy(isArray = false, isReference = false)

        return baseClass
            .parameterizedBy(nonArrayCopy.asClassName(nullable = !nonArrayCopy.isPrimitiveSystemType()))
    }
    if (nullable) {
        return asClassName(isReference).copy(true)
    }
    if (namespace == "System") {
        return when (name) {
            "Single" -> Float::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Byte" -> Byte::class.asClassName()
            "Int16" -> Short::class.asClassName()
            "Int32" -> Int::class.asClassName()
            "Int64" -> Long::class.asClassName()
            "Char" -> Char::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "UInt32" -> WinDef.UINT::class.asClassName()
            "String" -> String::class.asClassName()
            "UInt32&" -> WinDef.UINTByReference::class.asClassName()
            "Object" -> ClassName("com.github.knk190001.winrtbinding.runtime.interfaces", "IUnknown")
            "UInt64" -> ULONG::class.asClassName()
            "UInt16" -> USHORT::class.asClassName()
            "Guid" -> Guid.GUID::class.asClassName()
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (this.isReference) {
        if (genericParameters != null) {
            return ClassName(dropGenericParameterCount().name, "ByReference")
        }
        return ClassName(fullName(), "ByReference")
    }
    if (genericParameters != null) {
        return ClassName(this.namespace, cleanName())
    }

    if (lookUpTypeReference(this) is SparseStruct && structByValue) {
        return ClassName(namespace, name).nestedClass("ByValue")
    }
    return ClassName(this.namespace, this.name)
}

fun SparseTypeReference.asKClass(): KClass<*> {
    if (isArray) return WinRTTypeVariable::class
    if(namespace.isEmpty()) return Nothing::class
    if (namespace == "System") {
        when (name) {
            "UInt32" -> return WinDef.UINT::class
            "UInt64" -> return ULONG::class
            "Double" -> return Double::class
            "Boolean" -> return Boolean::class
            "Int16" -> return Short::class
            "Int32" -> return Int::class
            "Int64" -> return Long::class
            "Void" -> return Unit::class
            "String" -> return String::class
            "UInt32&" -> return WinDef.UINTByReference::class
            "UInt16" -> return USHORT::class
            "Object" -> return IUnknown::class
            "Single" -> return Float::class
            "Char" -> return Char::class
            "Byte" -> return Byte::class
            "Guid" -> return Guid.GUID::class
        }
    }

    return when (lookUpTypeReference(this)) {
        is SparseClass -> WinRTClass::class
        is SparseInterface -> {
            if (genericParameters != null) {
                WinRTGenericInterface::class
            } else {
                WinRTInterface::class
            }
        }
        is SparseDelegate -> {
            if (genericParameters != null) {
                WinRTGenericDelegate::class
            } else {
                WinRTDelegate::class
            }
        }
        is SparseEnum -> WinRTEnum::class
        is SparseStruct -> WinRTStruct::class
        else -> Nothing::class
    }
}

fun SparseTypeReference.byReferenceClassName(): TypeName {
    if (isArray) {
        return ClassName("com.github.knk190001.winrtbinding.runtime", "OutArray")
            .parameterizedBy(copy(isReference = false, isArray = false).asClassName())

    }
    if (namespace == "System") {
        return when (name) {
            "UInt16" -> USHORTByReference::class.asClassName()
            "UInt32" -> WinDef.UINTByReference::class.asClassName()
            "UInt64" -> WinDef.ULONGByReference::class.asClassName()
            "Single" -> FloatByReference::class.asClassName()
            "Double" -> DoubleByReference::class.asClassName()
            "Boolean" -> ByteByReference::class.asClassName()
            "Int16" -> ShortByReference::class.asClassName()
            "Int32" -> IntByReference::class.asClassName()
            "Int64" -> LongByReference::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "String" -> WinNT.HANDLEByReference::class.asClassName()
            "Object" -> ClassName("com.github.knk190001.winrtbinding.runtime.interfaces.IUnknown", "ByReference")
            "Byte" -> ByteByReference::class.asClassName()
            "Guid" -> Guid.GUID.ByReference::class.asClassName()
            "Char" -> CharByReference::class.asClassName()
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (genericParameters != null) {
        val name = dropGenericParameterCount().name
        val typeParameters = genericParameters?.map { it.type!!.asGenericTypeParameter().copy(!it.type.isPrimitiveSystemType()) }?: emptyList()
        return ClassName("${this.namespace}.$name", "ByReference").parameterizedBy(typeParameters)
    }

    return ClassName(this.namespace + ".${this.name}", "ByReference")
}

fun TypeSpec.Builder.addSignatureAnnotation(sparseInterface: INamedEntity) {
    val annotation = AnnotationSpec.builder(Signature::class).apply {
        addMember("%S", GuidGenerator.getSignature(sparseInterface.asTypeReference(), lookUpTypeReference))
    }.build()
    addAnnotation(annotation)
}

fun TypeSpec.Builder.addGuidAnnotation(guid: String) {
    val annotation = AnnotationSpec.builder(com.github.knk190001.winrtbinding.runtime.Guid::class).apply {
        addMember("%S", guid)
    }.build()
    addAnnotation(annotation)
}


//val reservedWords = listOf("as","break","class","continue","do","else","false","for","fun","if","in","interface","null","object","package","return", "super", "this","throw", "true", "try", "tyoe")
val reservedWords = listOf("package", "object")