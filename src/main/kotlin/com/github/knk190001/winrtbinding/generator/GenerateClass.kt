@file:Suppress("DuplicatedCode")

package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.github.knk190001.winrtbinding.runtime.JNAApiInterface
import com.github.knk190001.winrtbinding.runtime.base.IABI
import com.github.knk190001.winrtbinding.runtime.com.IActivationFactory
import com.github.knk190001.winrtbinding.runtime.com.IUnknownVtbl
import com.github.knk190001.winrtbinding.runtime.com.IWinRTInterface
import com.github.knk190001.winrtbinding.runtime.com.IWinRTObject
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.ptr.PointerByReference
import java.lang.foreign.MemoryAddress
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import kotlin.math.abs
import kotlin.reflect.KType

private val jnaPointer = ClassName("com.github.knk190001.winrtbinding.runtime", "JNAPointer")
private val ptrNull = jnaPointer.member("NULL")

fun generateClass(
    sparseClass: SparseClass,
    lookUp: LookUp) = FileSpec.builder(sparseClass.namespace, sparseClass.name).apply {
    addImports()
    val classTypeSpec = TypeSpec.classBuilder(sparseClass.name).apply {
        addABIAnnotation(sparseClass.asTypeReference().asClassName())
        addSignatureAnnotation(sparseClass)
        superclass(PointerType::class)
        sparseClass.interfaces.forEach {
            if (lookUpTypeReference(it) is SparseInterface && !it.hasGenericParameter()) {
                addSuperinterface(it.asClassName(nestedClass = "WithDefault"))
            } else {
                addSuperinterface(it.asGenericTypeParameter(false))
            }
            addSuperinterface(it.asGenericTypeParameter())
        }
        addSuperinterface(IWinRTObject::class)

        generateClassTypeSpec(sparseClass)
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
        addParameter(it.name, it.type.asGenericTypeParameter(false))
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
        addSuperinterface(
            IABI::class.asClassName().parameterizedBy(
                ClassName("", sparseClass.name),
                MemoryAddress::class.asClassName()
            )
        )
        if (sparseClass.isDirectlyActivatable) {
            generateDirectActivationCode(sparseClass)
        }

        if (sparseClass.hasStaticInterfaces) {
            generateStaticInterfaces(sparseClass)
        }

        if (sparseClass.hasFactoryActivator) {
            generateFactoryActivationCode(sparseClass)
        }
        addFromNative(sparseClass)
        addPtrToNative(sparseClass)
        addLayout()
    }.build()

    addType(abiSpec)
}

private fun TypeSpec.Builder.addLayout() {
    val layoutSpec = PropertySpec.builder("layout", MemoryLayout::class).apply {
        initializer("%T.ADDRESS",ValueLayout::class)
        addModifiers(KModifier.OVERRIDE)
    }.build()
    addProperty(layoutSpec)
}

private fun TypeSpec.Builder.addFromNative(sparseClass: SparseClass) {
    val fromNative = FunSpec.builder("fromNative").apply {
        addModifiers(KModifier.OVERRIDE)
        addParameter("segment", MemoryAddress::class)
        returns(sparseClass.asTypeReference().asClassName())
        addStatement("val address = segment.toRawLongValue()", ValueLayout::class.member("ADDRESS"))
        addStatement("return %T(%T(address))".fixSpaces(), sparseClass.asTypeReference().asClassName(), Pointer::class)
    }.build()
    addFunction(fromNative)
}

private fun TypeSpec.Builder.generateFactoryActivationCode(sparseClass: SparseClass) {
    generateFactoryActivators(sparseClass)
    generateFactoryLazyProperties(sparseClass)
    generateFactoryActivationFunctions(sparseClass)
}

private fun TypeSpec.Builder.generateFactoryActivationFunctions(sparseClass: SparseClass) {
    val factoryInterfaces = sparseClass.factoryActivatorTypes.map {
        lookUpTypeReference(it) as SparseInterface
    }

    factoryInterfaces.flatMap {
        it.methods.map { method -> method to it }
    }.forEach {
        generateFactoryActivationFunction(it.first, it.second)
    }
}

fun TypeSpec.Builder.generateFactoryActivationFunction(
    method: SparseMethod,
    factoryInterface: SparseInterface
) {
    val activationFn = FunSpec.builder("activate").apply {
        method.parameters.forEach {
            addParameter(it.name, it.type.asGenericTypeParameter(false))
        }
        returns(jnaPointer.copy(true))
        val cb = CodeBlock.builder().apply {
            add("return ${factoryInterface.name}_Instance.${method.name}(")
            add(method.parameters.joinToString { it.name })
            add(")?.pointer")
        }.build()
        addCode(cb)
    }.build()
    addFunction(activationFn)
}

private fun TypeSpec.Builder.generateFactoryLazyProperties(sparseClass: SparseClass) {
    sparseClass.factoryActivatorTypes.forEach {
        generateFactoryLazyProperty(lookUpTypeReference(it) as SparseInterface)
    }
}

private fun TypeSpec.Builder.generateFactoryLazyProperty(factoryInterface: SparseInterface) {
    val factoryInterfaceProperty = PropertySpec.builder(
        "${factoryInterface.name}_Instance",
        factoryInterface.asTypeReference().asClassName()
    ).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("create${factoryInterface.name}()")
            endControlFlow()
        }.build()

        delegate(delegateCb)
    }.build()
    addProperty(factoryInterfaceProperty)
}

private fun TypeSpec.Builder.generateFactoryActivators(sparseClass: SparseClass) {
    sparseClass.factoryActivatorTypes.forEach {
        generateFactoryActivator(sparseClass, it)
    }
}

private fun TypeSpec.Builder.generateFactoryActivator(sparseClass: SparseClass, factoryInterface: SparseTypeReference) {
    val createFactoryActivatorFn = FunSpec.builder("create${factoryInterface.name}").apply {
        val factoryClassName = factoryInterface.asClassName() as ClassName
        val cb = CodeBlock.builder().apply {
            val iidMember = factoryClassName.nestedClass("ABI").member("IID")
            addStatement("val refiid = %T(%M)", REFIID::class, iidMember)
            addStatement("val factoryActivatorPtr = %T()", PointerByReference::class)

            val win32 = ClassName("com.github.knk190001.winrtbinding.runtime", "JNAApiInterface")
                .nestedClass("Companion")
                .member("INSTANCE")

            addStatement(
                "val hr = %M.RoGetActivationFactory(%S.toHandle(),refiid,factoryActivatorPtr)",
                win32,
                sparseClass.fullName()
            )
            addStatement("checkHR(hr)")
            addStatement(
                "return(%T.ABI.make${factoryInterface.name}(factoryActivatorPtr.value))",
                factoryClassName
            )
        }.build()
        addCode(cb)
        returns(factoryClassName)
    }.build()
    addFunction(createFactoryActivatorFn)
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

            val win32 = ClassName("com.github.knk190001.winrtbinding.runtime", "JNAApiInterface")
                .nestedClass("Companion")
                .member("INSTANCE")

            addStatement(
                "val hr = %M.RoGetActivationFactory(%S.toHandle(),refiid,interfacePtr)",
                win32,
                sparseClass.fullName()
            )
            addStatement(
                "val result = %T.ABI.make${staticInterface.name}(interfacePtr.value)",
                ClassName(staticInterface.namespace, staticInterface.name)
            )
            addStatement("return result")
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
    generateActivationFactoryPropertySpec()
    generateActivationFunction()
}

private fun TypeSpec.Builder.generateActivationFunction() {
    val activateSpec = FunSpec.builder("activate").apply {
        returns(jnaPointer)
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

private fun TypeSpec.Builder.generateActivationFactoryPropertySpec() {
    val activationFactoryClass = IActivationFactory::class
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
        val cb = CodeBlock.builder().apply {
            val iActivationFactoryVtblIID = IActivationFactory.Companion::class.member("IID")
            addStatement("val refiid = %T(%M)", REFIID::class, iActivationFactoryVtblIID)
            addStatement("val iAFPtr = %T()", PointerByReference::class)

            val win32 = JNAApiInterface.Companion::class
                .member("INSTANCE")

            addStatement(
                "var hr = %M.RoGetActivationFactory(%S.toHandle(), refiid, iAFPtr)",
                win32,
                sparseClass.fullName()
            )
            addStatement("checkHR(hr)")


            addStatement("return %T(iAFPtr.value)", IActivationFactory::class)
        }.build()
        addCode(cb)
        returns(IActivationFactory::class)
    }.build()
    addFunction(createActivationFunSpec)
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding.runtime", "getValue")
    addImport("com.github.knk190001.winrtbinding.runtime.interop", "guidOf")
    addImport("com.github.knk190001.winrtbinding.runtime", "toHandle")
    addImport("com.github.knk190001.winrtbinding.runtime", "checkHR")
    addImport("kotlin.reflect", "typeOf")

}

private fun TypeSpec.Builder.generateClassTypeSpec(
    sparseClass: SparseClass,
) {
    generateConstructor(sparseClass)
    generateQueryInterfaceMethods(sparseClass)
    generateLazyInterfaceProperties(sparseClass)
    generateTypeProperties(sparseClass)
    generateInterfacePointerProperties(sparseClass)
    generateInterfaceArray(sparseClass)
}

private fun TypeSpec.Builder.generateTypeProperties(sparseClass: SparseClass) {
    val genericInterfaces = sparseClass.interfaces
        .filter { it.hasActualizedGenericParameter() }

    genericInterfaces.map {
        PropertySpec.builder(it.getInterfaceTypeName(), KType::class, KModifier.OVERRIDE).apply {
            initializer("typeOf<%T>()", it.asGenericTypeParameter())
        }.build()
    }.forEach(this::addProperty)

}

private fun TypeSpec.Builder.generateInterfaceArray(sparseClass: SparseClass) {
    //Add a property that has all the interfaces in an array
    val iWinRTInterfaceClassName = IWinRTInterface::class.asClassName()
    val interfaceArray = PropertySpec.builder(
        "interfaces",
        Array::class.asClassName().parameterizedBy(iWinRTInterfaceClassName),
        KModifier.OVERRIDE
    ).apply {
        val getterSpec = FunSpec.getterBuilder().apply {
            val cb = CodeBlock.builder().apply {
                add("return arrayOf(")
                add(sparseClass.interfaces.joinToString {
                    it.getInterfacePropertyName()
                })
                add(")\n")
            }.build()
            addCode(cb)
        }.build()
        getter(getterSpec)
    }.build()
    addProperty(interfaceArray)
}

private fun TypeSpec.Builder.generateInterfacePointerProperties(sparseClass: SparseClass) {
    sparseClass.interfaces.map {
        generateInterfacePointerProperty(it)
    }.forEach(::addProperty)
}

fun generateInterfacePointerProperty(sparseTypeReference: SparseTypeReference): PropertySpec {
    return PropertySpec.builder(
        sparseTypeReference.getInterfacePointerName(),
        jnaPointer.copy(true)
    ).apply {
        addModifiers(KModifier.OVERRIDE)
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("${sparseTypeReference.getInterfacePropertyName()}.${sparseTypeReference.getInterfacePointerName()}")
            endControlFlow()
        }.build()
        delegate(delegateCb)
    }.build()

}

private fun TypeSpec.Builder.generateLazyInterfaceProperties(sparseClass: SparseClass) {
    sparseClass.interfaces
        .map(::generateLazyInterfaceProperty)
        .forEach(this::addProperty)
}

private fun generateLazyInterfaceProperty(typeReference: SparseTypeReference): PropertySpec {
    val type = if (lookUpTypeReference(typeReference) is SparseInterface && !typeReference.hasGenericParameter()) {
        typeReference.asClassName(nestedClass = "WithDefault")
    } else {
        typeReference.asGenericTypeParameter()
    }

    return PropertySpec.builder(typeReference.getInterfacePropertyName(), type).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            addStatement("${typeReference.getCastFunctionName()}()")
            endControlFlow()
        }.build()
        delegate(delegateCb)
    }.build()
}

private fun TypeSpec.Builder.generateQueryInterfaceMethods(sparseClass: SparseClass) {
    sparseClass.interfaces
        .map(::generateQueryInterfaceMethod)
        .forEach(this::addFunction)
}

fun hashID(str: String): String {
    return "_${abs(str.hashCode())}"
}

fun SparseTypeReference.hashID(): String {
    return hashID("${namespace}${cleanName()}")
}

fun SparseTypeReference.getCastFunctionName(): String {
    return "as${hashID()}"
}

fun SparseTypeReference.getInterfacePropertyName(): String {
    return "_${hashID()}_Interface"
}

fun SparseTypeReference.getInterfacePointerName(): String {
    return "_${hashID()}_Ptr"
}

fun SparseTypeReference.getInterfaceTypeName(): String {
    return "_${hashID()}_Type"
}

private fun generateQueryInterfaceMethod(typeReference: SparseTypeReference) =
    FunSpec.builder(typeReference.getCastFunctionName()).apply {
        addModifiers(KModifier.PRIVATE)
        val cb = CodeBlock.builder().apply {
            beginControlFlow("if(pointer == %M)", ptrNull)
            val typeParams = typeReference.genericParameters?.joinToString { "%T" } ?: ""
            val infix = if (typeParams.isEmpty()) "" else "<$typeParams>"
            val typeParamClassnames = typeReference.genericParameters?.map {
                it.type!!.asGenericTypeParameter().copy(!it.type.isPrimitiveSystemType())
            }?.toTypedArray() ?: emptyArray()
            addStatement(
                "return(%T.ABI.make${typeReference.cleanName()}$infix(%M))",
                typeReference.asClassName(), *typeParamClassnames, ptrNull
            )
            endControlFlow()
            val iidStatement = "val refiid = %T(guidOf<%T>())"


            addStatement(iidStatement, REFIID::class, typeReference.asGenericTypeParameter())
            addStatement("val ref = %T()", PointerByReference::class)
            val iUnkownVtblClass = IUnknownVtbl::class
            addStatement("%T(pointer.getPointer(0)).queryInterface(pointer, refiid, ref)", iUnkownVtblClass)
            addStatement(
                "return(%T.ABI.make${typeReference.cleanName()}$infix(ref.value))",
                typeReference.asClassName(),
                *typeParamClassnames
            )

        }.build()
        addCode(cb)
        if (lookUpTypeReference(typeReference) is SparseInterface && !typeReference.hasGenericParameter()) {
            returns(typeReference.asClassName(nestedClass = "WithDefault"))
        } else {
            returns(typeReference.asGenericTypeParameter())
        }

    }.build()

private fun TypeSpec.Builder.generateConstructor(sparseClass: SparseClass) {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        val ptrParameterSpec = ParameterSpec.builder("ptr", jnaPointer.copy(true))
            .defaultValue("%M", ptrNull)
            .build()
        addParameter(ptrParameterSpec)
    }.build()
    primaryConstructor(constructorSpec)
    addSuperclassConstructorParameter("ptr")

    if (sparseClass.isDirectlyActivatable) {
        generateDirectActivationConstructor()
    }

    if (sparseClass.hasFactoryActivator) {
        generateFactoryConstructors(sparseClass)
    }


}

private fun TypeSpec.Builder.generateFactoryConstructors(sparseClass: SparseClass) {

    sparseClass.factoryActivatorTypes.map {
        lookUpTypeReference(it) as SparseInterface
    }.flatMap {
        it.methods
    }.forEach(::generateFactoryConstructor)
}

private fun TypeSpec.Builder.generateFactoryConstructor(sparseMethod: SparseMethod) {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        sparseMethod.parameters.forEach {
            addParameter(it.name, it.type.asGenericTypeParameter())
        }
        callThisConstructor("ABI.activate(${sparseMethod.parameters.joinToString { it.name }})")
    }.build()
    addFunction(constructorSpec)
}

private fun TypeSpec.Builder.generateDirectActivationConstructor() {
    val constructorSpec = FunSpec.constructorBuilder().apply {
        callThisConstructor("ABI.activate()")
    }.build()
    addFunction(constructorSpec)
}

