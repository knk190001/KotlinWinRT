package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.SparseStruct
import com.squareup.kotlinpoet.*
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder

fun generateStruct(sparseStruct: SparseStruct) = FileSpec.builder(sparseStruct.namespace, sparseStruct.name).apply {
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")
    val type = TypeSpec.classBuilder(sparseStruct.name).apply {
        addModifiers(KModifier.SEALED)

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


        val byReference = TypeSpec.classBuilder("ByReference").apply {
            superclass(ClassName(sparseStruct.namespace,sparseStruct.name))
            addSuperinterface(Structure.ByValue::class)

            val getValueFn = FunSpec.builder("getValue").apply {
                addCode("return this")
                returns(ClassName(sparseStruct.namespace,sparseStruct.name))
            }.build()
            addFunction(getValueFn)
        }.build()
        addType(byReference)

        primaryConstructor(constructor)
        addSuperclassConstructorParameter("ptr")
    }.build()
    addType(type)
}.build()