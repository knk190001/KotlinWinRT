package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Native
import com.sun.jna.NativeMapped
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid
import com.sun.jna.ptr.ByReference
import com.sun.jna.Function as JNAFunction

fun generateInterface(
    sparseInterface: SparseInterface,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) = FileSpec.builder(sparseInterface.namespace, sparseInterface.name).apply {
    addImports()

    val interfaceSpec = TypeSpec.interfaceBuilder(sparseInterface.name).apply {
        addSuperinterface(NativeMapped::class)
        addSuperinterface(ClassName("com.github.knk190001.winrtbinding.interfaces", "IWinRTInterface"))

        addProperty("${sparseInterface.name}_Ptr", Pointer::class.asClassName().copy(true))

        addVtblPtrProperty(sparseInterface)
        addMethods(sparseInterface, lookUp, projectInterface)
        addByReferenceType(sparseInterface)
        generateImplementation(sparseInterface, lookUp, projectInterface)
        addABI(sparseInterface, lookUp)
        generateCompanion(sparseInterface)
    }.build()
    addType(interfaceSpec)

}.build()

private fun TypeSpec.Builder.generateCompanion(sparseInterface: SparseInterface) {
    val companionSpec = TypeSpec.companionObjectBuilder().apply {
        generateMakeArrayFunction(sparseInterface)
        generateMakeArrayOfNullsFunction(sparseInterface)
    }.build()
    addType(companionSpec)
}

fun TypeSpec.Builder.generateMakeArrayOfNullsFunction(sparseInterface: SparseInterface) {
    val makeArrayOfNullsSpec = FunSpec.builder("makeArrayOfNulls").apply {
        addParameter("size", Int::class)
        val returnType = Array::class.asClassName().parameterizedBy(ClassName("", sparseInterface.name).copy(true))
        returns(returnType)
        val cb = CodeBlock.builder().apply {
            val implClassName = ClassName("", "${sparseInterface.name}_Impl")
            addStatement("return arrayOfNulls<%T>(size) as %T", implClassName, returnType)
        }.build()
        addCode(cb)
    }.build()
    addFunction(makeArrayOfNullsSpec)
}

private fun TypeSpec.Builder.generateMakeArrayFunction(sparseInterface: SparseInterface) {
    val makeArraySpec = FunSpec.builder("makeArray").apply {
        addParameter("elements", ClassName("", sparseInterface.name), KModifier.VARARG)
        returns(Array::class.asClassName().parameterizedBy(ClassName("", sparseInterface.name)))
        val cb = CodeBlock.builder().apply {
            val interfaceClassName = ClassName("", sparseInterface.name)
            addStatement(
                "return (elements as Array<%T>).castToImpl<%T,${sparseInterface.name}_Impl>()",
                interfaceClassName,
                interfaceClassName
            )
        }.build()
        addCode(cb)
    }.build()
    addFunction(makeArraySpec)
}

private fun TypeSpec.Builder.generateImplementation(
    sparseInterface: SparseInterface,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) {
    val implementationSpec = TypeSpec.classBuilder("${sparseInterface.name}_Impl").apply {
        superclass(PointerType::class)
        addSuperinterface(ClassName("", sparseInterface.name))

        addPointerProperty(sparseInterface)
//        addVtblPtrProperty()
        addConstructor()
//        addMethods(sparseInterface, lookUp, projectInterface)
    }.build()
    addType(implementationSpec)
}

private fun TypeSpec.Builder.addPointerProperty(sparseInterface: SparseInterface) {
    val getter = FunSpec.getterBuilder()
        .addCode("return pointer")
        .build()

    val pointerPropertySpec =
        PropertySpec.builder("${sparseInterface.name}_Ptr", Pointer::class.asClassName().copy(true))
            .getter(getter)
            .addModifiers(KModifier.OVERRIDE)
            .build()
    addProperty(pointerPropertySpec)
}

private fun TypeSpec.Builder.addByReferenceType(sparseInterface: SparseInterface) {
    val spec = TypeSpec.classBuilder("ByReference").apply {
        generateByReferenceInterface(sparseInterface)
    }.build()
    addType(spec)
}

internal fun TypeSpec.Builder.generateByReferenceInterface(entity: SparseInterface) {
    val className = ClassName("", entity.name)

    superclass(ByReference::class)
    val ptrSize = Native::class.member("POINTER_SIZE")
    addSuperclassConstructorParameter("%M", ptrSize)

    val getValueSpec = FunSpec.builder("getValue").apply {
        addCode("return ABI.make${entity.name}(pointer.getPointer(0))", className)
    }.build()
    addFunction(getValueSpec)

    val setValueSpec = FunSpec.builder("setValue").apply {
        addParameter("value", ClassName("", "${entity.name}_Impl"))
        addCode("pointer.setPointer(0, value.pointer)")
    }.build()
    addFunction(setValueSpec)
}

private fun TypeSpec.Builder.addABI(sparseInterface: SparseInterface, lookUp: LookUp) {
    val abiSpec = TypeSpec.objectBuilder("ABI").apply {
        if (sparseInterface.genericParameters != null && sparseInterface.genericParameters.any { it.type != null }) {
            addPIIDProperty(sparseInterface, lookUp)
        }
        generateMakeFunction(sparseInterface)
        addIIDProperty(sparseInterface)
    }.build()
    addType(abiSpec)
}

private fun TypeSpec.Builder.generateMakeFunction(sparseInterface: SparseInterface) {
    val makeFn = FunSpec.builder("make${sparseInterface.name}").apply {
        addParameter("ptr", Pointer::class.asClassName().copy(true))
        returns(ClassName("", sparseInterface.name))
        addCode("return %T(ptr)", ClassName("", "${sparseInterface.name}_Impl"))
    }.build()
    addFunction(makeFn)
}

private fun TypeSpec.Builder.addIIDProperty(sparseInterface: SparseInterface) {
    val iidSpec = PropertySpec.builder("IID", Guid.IID::class.asClassName()).apply {
        this.initializer("%T(%S)", Guid.IID::class.asClassName(), sparseInterface.guid)
    }.build()
    addProperty(iidSpec)
}

private fun TypeSpec.Builder.addPIIDProperty(
    sparseInterface: SparseInterface,
    lookUp: LookUp
) {
    val piid = GuidGenerator.CreateIID(sparseInterface.asTypeReference(), lookUp)!!.toGuidString()
        .filter { it.isLetterOrDigit() }
        .lowercase()

    val piidSpec = PropertySpec.builder("PIID", Guid.IID::class).apply {
        initializer(CodeBlock.of("%T(%S)", Guid.IID::class, piid))
    }.build()
    addProperty(piidSpec)
}

private fun TypeSpec.Builder.addMethods(
    sparseInterface: SparseInterface,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) {
    sparseInterface.methods
        .mapIndexed { index, method ->
            projectMethodTypes(method, lookUp, projectInterface)

            FunSpec.builder(method.name).apply {
                method.parameters.forEach { addParameter(it.name, it.type.asClassName()) }
                addInterfaceMethodBody(method, index, sparseInterface)
                if (!method.returnType.isVoid()) returns(method.returnType.asClassName(false))
            }.build()
        }.forEach(this::addFunction)
}

private fun TypeSpec.Builder.addInterfaceMethods(sparseInterface: SparseInterface) {
    sparseInterface.methods.map { method ->
        FunSpec.builder(method.name).apply {
            addModifiers(KModifier.ABSTRACT)
            method.parameters.forEach { addParameter(it.name, it.type.asClassName()) }
            returns(method.returnType.asClassName(false))
        }.build()
    }.forEach { addFunction(it) }

}

private fun isMethodValid(method: SparseMethod): Boolean {
    return method.parameters.none { it.type.isArray } && !method.returnType.isArray
}

private fun FunSpec.Builder.addInterfaceMethodBody(method: SparseMethod, index: Int, sparseInterface: SparseInterface) {
    val cb = CodeBlock.builder().apply {
        generateInterfaceMethodBody(method, index, sparseInterface)
    }.build()
    addCode(cb)
}

private fun CodeBlock.Builder.generateInterfaceMethodBody(
    method: SparseMethod,
    index: Int,
    sparseInterface: SparseInterface
) {
    val pointerSize = Native::class.member("POINTER_SIZE")
    val stdConvention = JNAFunction::class.member("ALT_CONVENTION")
    addStatement("val fnPtr = ${sparseInterface.vtblName()}!!.getPointer(${index + 6}L * %M)", pointerSize)
    addStatement("val fn = %T.getFunction(fnPtr, %M)", JNAFunction::class, stdConvention)

    val marshalledNames = marshalParameters(method)

    if (!method.returnType.isVoid()) {
        if (method.returnType.isArray) {
            addStatement(
                "val result = makeOutArray<%T>()",
                method.returnType.copy(isArray = false, isReference = false).asClassName()
            )
        } else {
            addStatement("val result = %T()", method.returnType.byReferenceClassName())
        }
    }

    add("val hr = fn.invokeHR(arrayOf(${sparseInterface.name}_Ptr, ")
    add(marshalledNames.joinToString())

    if (method.parameters.isNotEmpty()) {
        add(",")
    }

    if (!method.returnType.isVoid()) {
        add(" result")
    }
    add("))\n")

    beginControlFlow("if (hr.toInt() != 0) {")
    addStatement("throw %T(hr.toString())", RuntimeException::class.asClassName())
    endControlFlow()

    if (method.returnType.isVoid()) return

    val returnMarshaller = Marshaller.marshals.getOrDefault(method.returnType.asKClass(), Marshaller.default)
    val (unmarshalledName, unmarshallingCode) = returnMarshaller.generateFromNativeMarshalCode("resultValue")

    if (method.returnType.isArray) {
        addStatement("val resultValue = result.array")
    } else {
        addStatement("val resultValue = result.getValue()")
    }


    add(unmarshallingCode)
    addStatement("return $unmarshalledName")
}

private fun SparseTypeReference.isVoid(): Boolean {
    return namespace == "System" && name == "Void"
}

private fun CodeBlock.Builder.marshalParameters(method: SparseMethod): List<String> {
    return method.parameters.map {
        val (newName, marshalCode) = Marshaller.marshals
            .getOrDefault(it.type.asKClass(), Marshaller.default)
            .generateToNativeMarshalCode(it.name)
        add(marshalCode)

        if (it.type.isArray) {
            "$newName.size, $newName"
        } else newName
    }
}

private fun projectMethodTypes(
    method: SparseMethod,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) {
    method.parameters
        .map { it.type }
        .forEach { projectType(it, lookUp, projectInterface) }

    projectType(method.returnType, lookUp, projectInterface)
}

private fun projectType(typeReference: SparseTypeReference, lookUp: LookUp, projectInterface: ProjectInterface) {
    if (typeReference.genericParameters != null) {
        projectInterface(lookUp(typeReference) as IDirectProjectable<*>, typeReference.genericParameters)
    }
}

private fun TypeSpec.Builder.addConstructor() {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        val ptrParameterSpec = ParameterSpec.builder("ptr", Pointer::class.asClassName().copy(true))
            .defaultValue("Pointer.NULL")
            .build()
        addParameter(ptrParameterSpec)
    }.build()
    primaryConstructor(constructorSpec)
    addSuperclassConstructorParameter("ptr")

}

fun SparseInterface.vtblName(): String {
    return "${name}_VtblPtr"
}

private fun TypeSpec.Builder.addVtblPtrProperty(sparseInterface: SparseInterface) {
    val getterSpec = FunSpec.getterBuilder().addCode("return ${sparseInterface.name}_Ptr?.getPointer(0)").build()

    val vtblPtrSpec = PropertySpec.builder("${sparseInterface.name}_VtblPtr", Pointer::class.asClassName().copy(true))
        .getter(getterSpec)
        .build()

    addProperty(vtblPtrSpec)
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding", "handleToString")
    addImport("com.github.knk190001.winrtbinding", "toHandle")
    addImport("com.github.knk190001.winrtbinding", "makeOutArray")
    addImport("com.github.knk190001.winrtbinding", "invokeHR")
    addImport("com.github.knk190001.winrtbinding", "castToImpl")
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")
}

