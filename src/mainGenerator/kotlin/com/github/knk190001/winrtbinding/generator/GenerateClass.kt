@file:Suppress("DuplicatedCode")

package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.ptr.PointerByReference

fun generateClass2(
    sparseClass: SparseClass,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) = FileSpec.builder(sparseClass.namespace, sparseClass.name).apply {
    addImports()
    projectInterfaces(sparseClass, lookUp, projectInterface)
    val classTypeSpec = TypeSpec.classBuilder(sparseClass.name).apply {
        superclass(PointerType::class)
        generateClassTypeSpec(sparseClass, lookUp)
        addByReferenceType(sparseClass)
        generateClassABI(sparseClass)
        if (sparseClass.hasStaticInterfaces) {
            generateCompanion(sparseClass, lookUp)
        }
    }.build()
    addType(classTypeSpec)
}.build()

private fun TypeSpec.Builder.generateCompanion(sparseClass: SparseClass, lookUp: LookUp) {
    val interfaces = sparseClass.staticInterfaces.map(lookUp)
        .filterIsInstance<SparseInterface>()
    val companion = TypeSpec.companionObjectBuilder().apply {
        interfaces.forEach { staticInterface ->
            staticInterface.methods.map { method: SparseMethod ->
                generateStaticMethod(method, staticInterface)
            }.forEach(this::addFunction)
        }
    }.build()
    addType(companion)
}

private fun generateStaticMethod(
    method: SparseMethod,
    staticInterface: SparseInterface
) = FunSpec.builder(method.name).apply {
    method.parameters.forEach {
        addParameter(it.name, it.type.asClassName())
    }
    val cb = CodeBlock.builder().apply {
        add("return ABI.${staticInterface.name}_Instance.${method.name}(")
        add(method.parameters.joinToString { it.name })
        add(")\n")
    }.build()
    addCode(cb)
}.build()

private fun TypeSpec.Builder.generateClassABI(sparseClass: SparseClass) {
    val abiSpec = TypeSpec.objectBuilder("ABI").apply {
        if (sparseClass.isDirectlyActivatable) {
            generateDirectActivationCode(sparseClass)
        }

        if (sparseClass.hasStaticInterfaces) {
            generateStaticInterfaces(sparseClass)
        }
    }.build()

    addType(abiSpec)
}

private fun TypeSpec.Builder.generateStaticInterfaces(sparseClass: SparseClass) {
    sparseClass.staticInterfaces.map {
        generateStaticInterface(it, sparseClass)
    }
}

fun TypeSpec.Builder.generateStaticInterface(staticInterface: SparseTypeReference, sparseClass: SparseClass) {
    val staticInterfaceClass = staticInterface.asClassName()
    val createStaticInterfaceSpec = FunSpec.builder("create${staticInterface.name}").apply {
        returns(staticInterfaceClass)
        val cb = CodeBlock.builder().apply {
            addStatement("val refiid = %T(%T.ABI.IID)", REFIID::class, staticInterfaceClass)
            addStatement("val interfacePtr = %T()", PointerByReference::class)

            val win32 = ClassName("com.github.knk190001.winrtbinding","JNAApiInterface")
                .nestedClass("Companion")
                .member("INSTANCE")

            addStatement(
                "val hr = %M.RoGetActivationFactory(%S.toHandle(),refiid,interfacePtr)",
                win32,
                sparseClass.fullName()
            )
            addStatement("return %T(interfacePtr.value)", ClassName(staticInterface.namespace, staticInterface.name))
        }.build()

        addCode(cb)
    }.build()

    val staticInterfaceProperty = PropertySpec.builder("${staticInterface.name}_Instance", staticInterfaceClass).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("create${staticInterface.name}()")
            endControlFlow()
        }.build()

        delegate(delegateCb)
    }.build()

    addFunction(createStaticInterfaceSpec)
    addProperty(staticInterfaceProperty)
}

private fun TypeSpec.Builder.generateDirectActivationCode(sparseClass: SparseClass) {
    generateCreateActivationFactorySpec(sparseClass)
    generateActivationFactoryPropertySpec(sparseClass)
    generateActivationFunction(sparseClass)
}

private fun TypeSpec.Builder.generateActivationFunction(sparseClass: SparseClass) {
    val activateSpec = FunSpec.builder("activate").apply {
        returns(Pointer::class)
        val cb = CodeBlock.builder().apply {
            addStatement("val result = %T()", PointerByReference::class)
            addStatement("val hr = activationFactory.activateInstance(activationFactory.ptr!!, result)")
            addStatement("checkHR(hr)")
            addStatement("return result.value")
        }.build()
        addCode(cb)
    }.build()
    addFunction(activateSpec)
}

private fun TypeSpec.Builder.generateActivationFactoryPropertySpec(sparseClass: SparseClass) {
    val activationFactoryClass = ClassName("com.github.knk190001.winrtbinding.interfaces", "IActivationFactory")
    val activationFactoryPropertySpec = PropertySpec.builder("activationFactory", activationFactoryClass).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("createActivationFactory()")
            endControlFlow()
        }.build()
        delegate(delegateCb)
    }.build()
    addProperty(activationFactoryPropertySpec)
}

private fun TypeSpec.Builder.generateCreateActivationFactorySpec(sparseClass: SparseClass) {

    val createActivationFunSpec = FunSpec.builder("createActivationFactory").apply {
        val iActivationFactoryClass =
            ClassName("com.github.knk190001.winrtbinding.interfaces", "IActivationFactory")
        val cb = CodeBlock.builder().apply {
            val iActivationFactoryVtblIID = ClassName(
                "com.github.knk190001.winrtbinding.interfaces.IActivationFactory",
                "Companion"
            ).member("IID")
            addStatement("val refiid = %T(%M)", REFIID::class, iActivationFactoryVtblIID)
            addStatement("val iAFPtr = %T()", PointerByReference::class)

            val win32 = ClassName("com.github.knk190001.winrtbinding","JNAApiInterface")
                .nestedClass("Companion")
                .member("INSTANCE")

            addStatement(
                "var hr = %M.RoGetActivationFactory(%S.toHandle(),refiid,iAFPtr)",
                win32,
                sparseClass.fullName()
            )
            addStatement("checkHR(hr)")


            addStatement("return %T(iAFPtr.value)", iActivationFactoryClass)
        }.build()
        addCode(cb)
        returns(iActivationFactoryClass)
    }.build()
    addFunction(createActivationFunSpec)
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")
    addImport("com.github.knk190001.winrtbinding", "toHandle")
    addImport("com.github.knk190001.winrtbinding", "checkHR")
}

private fun TypeSpec.Builder.generateClassTypeSpec(
    sparseClass: SparseClass,
    lookUp: LookUp,
) {
    generateConstructor(sparseClass)
    generateQueryInterfaceMethods(sparseClass)
    generateLazyInterfaceProperties(sparseClass)
    generateClassMethods(sparseClass, lookUp)
}

private fun TypeSpec.Builder.generateClassMethods(sparseClass: SparseClass, lookUp: LookUp) {
    sparseClass.interfaces
        .map { lookUp(it) to it}
        .mapFirst { it as SparseInterface }
        .mapPairFirst(::propagateTypeParameters)
        .flatMapFirst { it.methods }
        .filterFirst(::isMethodValid)
        .forEachPaired(::generateClassMethod)
}

private fun propagateTypeParameters(pair: Pair<SparseInterface, SparseTypeReference>): SparseInterface {
    val (sparseInterface, typeReference) = pair
    if (typeReference.genericParameters == null) return sparseInterface
    return typeReference.genericParameters.fold(sparseInterface) { acc, genericParameter ->
        acc.projectType(genericParameter.name, genericParameter.type!!)
    }
}

private fun isMethodValid(method: SparseMethod): Boolean {
    return method.parameters.none { it.type.isArray } && !method.returnType.isArray
}


private fun TypeSpec.Builder.generateClassMethod(sparseMethod: SparseMethod, typeReference: SparseTypeReference) {
    val fn = FunSpec.builder(sparseMethod.name).apply {
        sparseMethod.parameters.forEach {
            addParameter(it.name, it.type.asClassName())
        }
        returns(sparseMethod.returnType.asClassName())
        val name = typeReference.getProjectedName()
        val cb = CodeBlock.builder().apply {
            add("return ${name}_Interface.${sparseMethod.name}(")
            add(sparseMethod.parameters.joinToString { it.name })
            add(")")
        }.build()
        addCode(cb)
    }.build()
    addFunction(fn)
}


private fun TypeSpec.Builder.generateLazyInterfaceProperties(sparseClass: SparseClass) {
    sparseClass.interfaces
//        .map(SparseTypeReference::withProjectedName)
        .map(::generateLazyInterfaceProperty)
        .forEach(this::addProperty)
}

private fun generateLazyInterfaceProperty(typeReference: SparseTypeReference) =
    PropertySpec.builder("${typeReference.getProjectedName()}_Interface", typeReference.asClassName()).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("as${typeReference.getProjectedName()}()")
            endControlFlow()
        }.build()
        delegate(delegateCb)
    }.build()

private fun TypeSpec.Builder.generateQueryInterfaceMethods(sparseClass: SparseClass) {
    sparseClass.interfaces
//        .map(SparseTypeReference::withProjectedName)
        .map(::generateQueryInterfaceMethod)
        .forEach(this::addFunction)
}

private fun generateQueryInterfaceMethod(typeReference: SparseTypeReference) =
    FunSpec.builder("as${typeReference.getProjectedName()}").apply {
        addModifiers(KModifier.PRIVATE)
        val cb = CodeBlock.builder().apply {
            val iidStatement = if (typeReference.hasActualizedGenericParameter()) {
                "val refiid = %T(%T.ABI.PIID)"
            } else {
                "val refiid = %T(%T.ABI.IID)"
            }

            addStatement(iidStatement, REFIID::class, typeReference.asClassName())
            addStatement("val ref = %T()", PointerByReference::class)
            val iUnkownVtblClass = ClassName("com.github.knk190001.winrtbinding.interfaces", "IUnknownVtbl")
            addStatement("%T(pointer.getPointer(0)).queryInterface(pointer, refiid, ref)", iUnkownVtblClass)
            addStatement("return %T(ref.value)", typeReference.asClassName())

        }.build()
        addCode(cb)

        returns(typeReference.asClassName())

    }.build()

private fun TypeSpec.Builder.generateConstructor(sparseClass: SparseClass) {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        val ptrParameterSpec = ParameterSpec.builder("ptr", Pointer::class.asClassName().copy(true))
            .defaultValue("Pointer.NULL")
            .build()
        addParameter(ptrParameterSpec)
        addModifiers(KModifier.PRIVATE)
    }.build()
    primaryConstructor(constructorSpec)
    addSuperclassConstructorParameter("ptr")

    if (!sparseClass.isDirectlyActivatable) return
    generateDirectActivationConstructor()


}

private fun TypeSpec.Builder.generateDirectActivationConstructor() {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        callThisConstructor("ABI.activate()")
    }.build()
    addFunction(constructorSpec)
}

private fun projectInterfaces(sparseClass: SparseClass, lookUp: LookUp, projectInterface: ProjectInterface) {
    sparseClass.interfaces.filter {
        it.hasGenericParameter()
    }.forEach { typeReference ->
        val sparseInterface = lookUp(typeReference) as SparseInterface
        projectInterface(sparseInterface, typeReference.genericParameters!!)
        val projected = propagateTypeParameters(sparseInterface to typeReference)
        projected.methods
            .flatMap { it.parameters }
            .filter { it.type.hasActualizedGenericParameter() }
            .forEach { projectInterface(lookUp(it.type) as IDirectProjectable<*>, it.type.genericParameters!!) }

        projected.methods
            .map { it.returnType }
            .filter { it.hasActualizedGenericParameter() }
            .forEach { projectInterface(lookUp(it) as IDirectProjectable<*>, it.genericParameters!!) }
    }
}
