package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.SparseStruct
import com.squareup.kotlinpoet.*
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder

fun generateStruct(sparseStruct: SparseStruct) = FileSpec.builder(sparseStruct.namespace, sparseStruct.name).apply {
    val type = TypeSpec.classBuilder(sparseStruct.name).apply {
        val fields = sparseStruct.fields.sortedBy {
            it.index
        }

        val fieldOrderAnnotation = AnnotationSpec.builder(FieldOrder::class).apply {
            addMember("%S", fields.joinToString { it.name })
        }.build()
        addAnnotation(fieldOrderAnnotation)


        fields.map {
            PropertySpec.builder(it.name, it.type.asClassName().copy(true))
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

        primaryConstructor(constructor)
        addSuperclassConstructorParameter("ptr")
    }.build()
    addType(type)
}.build()