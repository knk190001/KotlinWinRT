package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.IDirectProjectable
import com.github.knk190001.winrtbinding.generator.model.entities.SparseClass
import com.squareup.kotlinpoet.*
import com.sun.jna.Pointer

fun generateClass(
    sparseClass: SparseClass,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) = FileSpec.builder(sparseClass.namespace, sparseClass.name).apply {
    addImports()
    projectInterfaces(sparseClass,lookUp, projectInterface)
    val classTypeSpec = TypeSpec.classBuilder(sparseClass.name).apply {
        addClassType(sparseClass,lookUp,projectInterface)
    }.build()
}.build()

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")
}

private fun TypeSpec.Builder.addClassType(
    sparseClass: SparseClass,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) {
    generateConstructor()

}

private fun TypeSpec.Builder.generateConstructor() {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        val ptrParameterSpec = ParameterSpec.builder("ptr",Pointer::class.asClassName().copy(true))
            .defaultValue("Pointer.NULL")
            .build()
        addParameter(ptrParameterSpec)
        addModifiers(KModifier.PRIVATE)
    }.build()
    primaryConstructor(constructorSpec)
    addSuperclassConstructorParameter("ptr")
}

private fun projectInterfaces(sparseClass: SparseClass,lookUp: LookUp,projectInterface: ProjectInterface) {
    sparseClass.interfaces.filter {
        it.name.contains("`") && it.genericParameters != null
    }.forEach {
        projectInterface(lookUp(it) as IDirectProjectable<*>, it.genericParameters!!)
    }
}
