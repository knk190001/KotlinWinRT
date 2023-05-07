package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.SparseStruct
import com.github.knk190001.winrtbinding.runtime.IByReference
import com.github.knk190001.winrtbinding.runtime.WinRTByReference
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder

fun generateStruct(sparseStruct: SparseStruct) = FileSpec.builder(sparseStruct.namespace, sparseStruct.name).apply {
    addImport("com.github.knk190001.winrtbinding.runtime.interfaces", "getValue")
    val type = TypeSpec.classBuilder(sparseStruct.name).apply {
        addModifiers(KModifier.SEALED)
        addSignatureAnnotation(sparseStruct)

        val fields = sparseStruct.fields.sortedBy {
            it.index
        }

        val fieldOrderAnnotation = AnnotationSpec.builder(FieldOrder::class).apply {
            addMember("%S", fields.joinToString { it.name })
        }.build()
        addAnnotation(fieldOrderAnnotation)


        fields.map {
            PropertySpec.builder(it.name, it.type.asGenericTypeParameter().copy(true))
                .initializer("null")
                .mutable()
                .build()
        }.forEach(::addProperty)

        superclass(Structure::class)
        val ptrParameterSpec = ParameterSpec.builder("ptr", Pointer::class.asClassName().copy(true))
            .defaultValue("%T.NULL", Pointer::class)
            .build()
        val constructor = FunSpec.constructorBuilder()
            .addParameter(ptrParameterSpec)
            .build()

        val brAnnotationSpec = AnnotationSpec.builder(WinRTByReference::class)
            .addMember("brClass = %L.ByReference::class", sparseStruct.name)
            .build()
        addAnnotation(brAnnotationSpec)

        val byReference = TypeSpec.classBuilder("ByReference").apply {
            superclass(ClassName(sparseStruct.namespace,sparseStruct.name))
            addSuperinterface(Structure.ByReference::class)
            addSuperinterface(IByReference::class.asClassName().parameterizedBy(ClassName("",sparseStruct.name)))


            val getValueFn = FunSpec.builder("getValue").apply {
                addModifiers(KModifier.OVERRIDE)
                addCode("return this")
                returns(ClassName(sparseStruct.namespace,sparseStruct.name))
            }.build()
            addFunction(getValueFn)
        }.build()

        val byValue = TypeSpec.classBuilder("ByValue").apply {
            superclass(ClassName(sparseStruct.namespace,sparseStruct.name))
            addSuperinterface(Structure.ByValue::class)
        }.build()

        addType(byReference)
        addType(byValue)
        primaryConstructor(constructor)
        addSuperclassConstructorParameter("ptr")
    }.build()
    addType(type)
}.build()