package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.ByReference
import com.sun.jna.Function as JNAFunction

fun generateInterface(
    sparseInterface: SparseInterface,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) = FileSpec.builder(sparseInterface.namespace, sparseInterface.name).apply {
    addImports()

    val interfaceSpec = TypeSpec.classBuilder(sparseInterface.name).apply {
        superclass(PointerType::class)
        addVtblPtrProperty()
        addConstructor()
        addMethods(sparseInterface, lookUp, projectInterface)
        addABI(sparseInterface, lookUp)
        addByReferenceType(sparseInterface)
    }.build()
    addType(interfaceSpec)

}.build()

private fun TypeSpec.Builder.addByReferenceType(sparseInterface: SparseInterface) {
    val spec = TypeSpec.classBuilder("ByReference").apply {
        generateByReferenceType(sparseInterface)
    }.build()
    addType(spec)
}

private fun TypeSpec.Builder.generateByReferenceType(sparseInterface: SparseInterface) {
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


private fun TypeSpec.Builder.addABI(sparseInterface: SparseInterface, lookUp: LookUp) {
    val abiSpec = TypeSpec.objectBuilder("ABI").apply {
        if (sparseInterface.genericParameters != null && sparseInterface.genericParameters.any { it.type != null }) {
            addPIIDProperty(sparseInterface, lookUp)
        }

        addIIDProperty(sparseInterface)
    }.build()
    addType(abiSpec)
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
        .filter(::isMethodValid)
        .mapIndexed { index, method ->
            projectMethodTypes(method, lookUp, projectInterface)

            FunSpec.builder(method.name).apply {
                method.parameters.forEach { addParameter(it.name, it.type.asClassName()) }
                addInterfaceMethodBody(method, index)
                if (!method.returnType.isVoid()) returns(method.returnType.asClassName())
            }.build()
        }.forEach(this::addFunction)
}

private fun isMethodValid(method: SparseMethod): Boolean {
    return method.parameters.none { it.type.isArray } && !method.returnType.isArray
}

private fun FunSpec.Builder.addInterfaceMethodBody(method: SparseMethod, index: Int) {
    val cb = CodeBlock.builder().apply {
        generateInterfaceMethodBody(method, index)
    }.build()
    addCode(cb)
}

private fun CodeBlock.Builder.generateInterfaceMethodBody(method: SparseMethod, index: Int) {
    val pointerSize = Native::class.member("POINTER_SIZE")
    val stdConvention = JNAFunction::class.member("ALT_CONVENTION")
    addStatement("val fnPtr = vtblPtr.getPointer(${index + 6}L * %M)", pointerSize)
    addStatement("val fn = %T.getFunction(fnPtr, %M)", JNAFunction::class ,stdConvention)

    val marshalledNames = marshalParameters(method)

    if (!method.returnType.isVoid()) {
        addStatement("val result = %T()", method.returnType.byReferenceClassName())
    }

    add("val hr = %T(fn.invokeInt(arrayOf(pointer, ", HRESULT::class)
    add(marshalledNames.joinToString())

    if (method.parameters.isNotEmpty()) {
        add(",")
    }

    if (!method.returnType.isVoid()) {
        add("result")
    }
    add(")))\n")

    beginControlFlow("if (hr.toInt() != 0) {")
    addStatement("throw %T(hr.toString())", RuntimeException::class.asClassName())
    endControlFlow()

    if (method.returnType.isVoid()) return

    val returnMarshaller = Marshaller.marshals.getOrDefault(method.returnType.asKClass(), Marshaller.default)
    val (unmarshalledName, unmarshallingCode) = returnMarshaller.generateFromNativeMarshalCode("resultValue")

    addStatement("val resultValue = result.getValue()")
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
        newName
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
        projectInterface(lookUp(typeReference) as DirectProjectable<*>, typeReference.genericParameters)
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

private fun TypeSpec.Builder.addVtblPtrProperty() {
    val getterSpec = FunSpec.getterBuilder().addCode("return pointer.getPointer(0)").build()

    val vtblPtrSpec = PropertySpec.builder("vtblPtr", Pointer::class)
        .getter(getterSpec)
        .build()

    addProperty(vtblPtrSpec)
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding", "handleToString")
    addImport("com.github.knk190001.winrtbinding", "toHandle")
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")
}

