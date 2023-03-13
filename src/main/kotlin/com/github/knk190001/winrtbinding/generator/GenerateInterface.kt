package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.github.knk190001.winrtbinding.runtime.interfaces.IUnknownVtbl
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Native
import com.sun.jna.NativeMapped
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.ptr.ByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.Function as JNAFunction

private val ptrNull = Pointer::class.asClassName().member("NULL")
private val jnaPointer = ClassName("com.github.knk190001.winrtbinding.runtime", "JNAPointer")

fun generateInterface(
    sparseInterface: SparseInterface,
    lookUp: LookUp,
    projectInterface: ProjectInterface
) = FileSpec.builder(sparseInterface.namespace, sparseInterface.name).apply {
    addImports()
    if (sparseInterface.genericParameters?.all { it.type == null } == true) {
        return generateParameterizedInterface(
            sparseInterface.withName(
                sparseInterface.name.replaceAfter('`', "").dropLast(1)
            )
        )
    }
    val interfaceSpec = TypeSpec.interfaceBuilder(sparseInterface.name).apply {
        addSuperInterfaces(sparseInterface)

        addProperty(
            sparseInterface.asTypeReference().getInterfacePointerName(),
            Pointer::class.asClassName().copy(true)
        )
        addAnnotation(JvmDefaultWithoutCompatibility::class)
        addVtblPtrProperty(sparseInterface)
        addMethods(sparseInterface, lookUp, projectInterface)
        addByReferenceType(sparseInterface)
        generateImplementation(sparseInterface, projectInterface)
        addABI(sparseInterface, lookUp)
        generateCompanion(sparseInterface)
    }.build()
    addType(interfaceSpec)

}.build()

fun generateParameterizedInterface(sparseInterface: SparseInterface): FileSpec {
    return FileSpec.builder(sparseInterface.namespace, sparseInterface.name).apply {
        addImports()
        addParameterizedInterface(sparseInterface)
    }.build()
}

private fun FileSpec.Builder.addParameterizedInterface(sparseInterface: SparseInterface) {
    val parameterizedInterface = TypeSpec.interfaceBuilder(sparseInterface.name).apply {
        addTypeParameters(sparseInterface)
        addGenericSuperInterfaces(sparseInterface)
        addGenericInterfaceMethods(sparseInterface)
        generateParameterizedByReference(sparseInterface)
    }.build()
    addType(parameterizedInterface)
}

private fun TypeSpec.Builder.addGenericInterfaceMethods(sparseInterface: SparseInterface) {
    sparseInterface.methods
        .map(::generateGenericInterfaceMethod)
        .forEach(this::addFunction)
}

fun generateGenericInterfaceMethod(method: SparseMethod): FunSpec =
    FunSpec.builder(method.name).apply {
        addModifiers(KModifier.ABSTRACT)
        method.parameters.forEach {
            if (it.type.namespace == "") {
                if (it.type.isArray) {
                    addParameter(
                        it.name,
                        Array::class.asClassName()
                            .parameterizedBy(TypeVariableName(it.type.name))
                    )
                } else {
                    addParameter(it.name, TypeVariableName.invoke(it.type.name))
                }
            } else {
                addParameter(it.name, it.type.asGenericTypeParameter(false).copy(!it.type.isPrimitiveSystemType()))
            }
        }
        if (method.returnType.namespace == "") {
            returns(TypeVariableName.invoke(method.returnType.name))
        } else {
            returns(method.returnType.asGenericTypeParameter(false))
        }
    }.build()

private fun TypeSpec.Builder.addGenericSuperInterfaces(sparseInterface: SparseInterface) {
    addSuperinterface(NativeMapped::class)
    sparseInterface.superInterfaces
        .map { it.asGenericTypeParameter() }
        .forEach(this::addSuperinterface)
}

fun SparseTypeReference.asGenericTypeParameter(structByValue: Boolean = true): TypeName {
    if (this.genericParameters == null && !isArray) {
        return this.asClassName(structByValue)
    }
    if (this.name.contains("`")) {
        return this.copy(
            name = name.replaceAfter('`', "").dropLast(1)
        ).asGenericTypeParameter(structByValue)
    }
    if (this.isArray) {
        val nonArray = this.copy(isArray = false)
        return Array::class
            .asClassName()
            .parameterizedBy(
                nonArray.asGenericTypeParameter(structByValue)
                    .copy(!nonArray.isPrimitiveSystemType())
            )
    }
    val typeParameters = genericParameters!!.map {
        if (it.type == null) {
            TypeVariableName(it.name)
        } else if (it.type!!.namespace == "") {
            TypeVariableName(it.type.name)
        } else {
            it.type.asGenericTypeParameter(structByValue).copy(!it.type.isPrimitiveSystemType())
        }
    }.toList()

    if (this.isReference) {
        return ClassName(namespace, name)
            .nestedClass("ByReference")
            .parameterizedBy(typeParameters)
    }
    return ClassName(namespace, name).parameterizedBy(typeParameters)

}

private fun TypeSpec.Builder.generateParameterizedByReference(sparseInterface: SparseInterface) {
    val byReferenceInterfaceSpec = TypeSpec.interfaceBuilder("ByReference").apply {
        addTypeParameters(sparseInterface)

        val getValueFn = FunSpec.builder("getValue").apply {
            if (sparseInterface.genericParameters != null) {
                addModifiers(KModifier.ABSTRACT)
            }
            returns(sparseInterface.asTypeReference().asGenericTypeParameter())
        }.build()
        addFunction(getValueFn)

    }.build()

    addType(byReferenceInterfaceSpec)
}

fun SparseTypeReference.dropGenericParameterCount(): SparseTypeReference {
    if (!this.name.contains('`')) return this
    return this.copy(name = name.replaceAfter('`', "").dropLast(1))
}

private fun TypeSpec.Builder.addTypeParameters(sparseInterface: SparseInterface) {
    sparseInterface.genericParameters!!
        .map(SparseGenericParameter::name)
        .map(TypeVariableName::invoke)
        .forEach(this::addTypeVariable)
}

private fun TypeSpec.Builder.addSuperInterfaces(sparseInterface: SparseInterface) {
    addSuperinterface(NativeMapped::class)
    if (sparseInterface.genericParameters != null) {
        val normalized = sparseInterface.asTypeReference().normalize()
        addSuperinterface(normalized.asGenericTypeParameter(false))
    }
    addSuperinterface(ClassName("com.github.knk190001.winrtbinding.runtime.interfaces", "IWinRTInterface"))
    sparseInterface.superInterfaces
        .map(SparseTypeReference::asClassName)
        .forEach(this::addSuperinterface)
}

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
        returns(Array::class.asClassName().parameterizedBy(ClassName("", sparseInterface.name).copy(true)))
        val cb = CodeBlock.builder().apply {
            val interfaceClassName = ClassName("", sparseInterface.name)
            addStatement(
                "return (elements as Array<%T?>).castToImpl<%T,${sparseInterface.name}_Impl>()",
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
    projectInterface: ProjectInterface
) {
    val implementationSpec = TypeSpec.classBuilder("${sparseInterface.name}_Impl").apply {
        superclass(PointerType::class)
        addSuperinterface(ClassName("", sparseInterface.name))
        addSuperInterfaces(sparseInterface)
        addSIProperties(sparseInterface, projectInterface)
        addPointerProperty(sparseInterface)
        addConstructor()
    }.build()
    addType(implementationSpec)
}

private fun TypeSpec.Builder.addSIProperties(sparseInterface: SparseInterface, projectInterface: ProjectInterface) {
    val superInterfaces = mutableMapOf<String, SparseTypeReference>()
    sparseInterface.superInterfaces.forEach { typeReference ->
        addSuperInterfacePtrProperties(sparseInterface, typeReference, projectInterface) {
            superInterfaces[it.getProjectedName()] = it
        }
    }

    superInterfaces.values.forEach {
        addSuperInterfacePtrProperty(sparseInterface, it)
    }
}

private fun TypeSpec.Builder.addSuperInterfacePtrProperties(
    thisInterface: SparseInterface,
    superInterface: SparseTypeReference,
    projectInterface: ProjectInterface,
    addProperty: (SparseTypeReference) -> Unit
) {
    val resolved = lookUpTypeReference(superInterface) as SparseInterface
    val projectedInterface = resolved
        .copy(genericParameters = superInterface.genericParameters)
        .projectAll()
    if (superInterface.genericParameters != null) {
        projectInterface(resolved, superInterface.genericParameters)
    }
    //addSuperInterfacePtrProperty(thisInterface, superInterface)
    addProperty(superInterface)
    projectedInterface.superInterfaces.forEach {
        addSuperInterfacePtrProperties(thisInterface, it, projectInterface, addProperty)
    }
}


private fun TypeSpec.Builder.addSuperInterfacePtrProperty(
    thisInterface: SparseInterface,
    superInterface: SparseTypeReference
) {
    val ptrSpec = PropertySpec.builder(
        superInterface.getInterfacePointerName(),
        jnaPointer,
        KModifier.OVERRIDE
    ).apply {
        val delegateCb = CodeBlock.builder().apply {
            beginControlFlow("lazy")
            val memberName = if (superInterface.genericParameters != null) {
                "PIID"
            } else {
                "IID"
            }
            val iid = (superInterface.asClassName() as ClassName).nestedClass("ABI").member(memberName)
            addStatement("val refiid = %T(%M)", REFIID::class, iid)
            addStatement("val result = %T()", PointerByReference::class)
            val iUnknownVtbl = IUnknownVtbl::class
            addStatement("%T(${thisInterface.vtblName()}).queryInterface(pointer,refiid,result)", iUnknownVtbl)
            addStatement("result.value")
            endControlFlow()
        }.build()
        delegate(delegateCb)
    }.build()

    addProperty(ptrSpec)
}

private fun TypeSpec.Builder.addPointerProperty(sparseInterface: SparseInterface) {
    val getter = FunSpec.getterBuilder()
        .addCode("return pointer")
        .build()

    val pointerPropertySpec =
        PropertySpec.builder(sparseInterface.pointerName(), Pointer::class.asClassName().copy(true))
            .getter(getter)
            .addModifiers(KModifier.OVERRIDE)
            .build()
    addProperty(pointerPropertySpec)
}

private fun SparseInterface.pointerName() =
    this.asTypeReference().getInterfacePointerName()

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
    if (entity.genericParameters != null) {
        val superinterface = ClassName(entity.namespace, entity.name.replaceAfter('_', "").dropLast(1))
            .nestedClass("ByReference")
            .parameterizedBy(entity.genericParameters!!.map {
                it.type!!.asGenericTypeParameter(false).copy(!it.type.isPrimitiveSystemType())
            })

        addSuperinterface(superinterface)
    }

    val getValueSpec = FunSpec.builder("getValue").apply {
        if (entity.genericParameters != null) {
            addModifiers(KModifier.OVERRIDE)
        }
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
                if (sparseInterface.genericParameters != null) {
                    addModifiers(KModifier.OVERRIDE)
                }
                method.parameters.forEach {
                    if (sparseInterface.genericParameters != null) {
                        addParameter(it.name,
                            it.type.asGenericTypeParameter(false)
                                .copy(!it.type.isPrimitiveSystemType() && !it.type.isArray)
                        )
                    } else {
                        addParameter(it.name, it.type.asClassName(false, nullable = !it.type.isPrimitiveSystemType()))
                    }

                }
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

    val marshalledNames = marshalParameters(method).map {
        if (reservedWords.contains(it)) {
            "`$it`"
        } else {
            it
        }
    }

    if (!method.returnType.isVoid()) {
        val nullable = if (method.returnType.copy(isArray = false).isPrimitiveSystemType()) {
            "Primitive"
        } else {
            ""
        }
        if (method.returnType.isArray) {
            addStatement(
                "val result = make${nullable}OutArray<%T>()",
                method.returnType.copy(isArray = false, isReference = false).asClassName()
            )
        } else {
            addStatement("val result = %T()", method.returnType.byReferenceClassName())
        }
    }

    add("val hr = fn.invokeHR(arrayOf(${sparseInterface.pointerName()}, ")
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
        add("val resultValue = result.array")
        if (!method.returnType.isSystemType() && lookUpTypeReference(
                method.returnType.copy(
                    isArray = false,
                    isReference = false
                )
            ) is SparseStruct
        ) {
            add(" as %T", method.returnType.copy(isReference = false).asClassName())
        }
        add("\n")
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
            .defaultValue("%M", ptrNull)
            .build()
        addParameter(ptrParameterSpec)
    }.build()
    primaryConstructor(constructorSpec)
    addSuperclassConstructorParameter("ptr")

}

fun SparseInterface.vtblName(): String {
    return "${asTypeReference().hashID()}_VtblPtr"
}

private fun TypeSpec.Builder.addVtblPtrProperty(sparseInterface: SparseInterface) {
    val getterSpec = FunSpec.getterBuilder().addCode("return ${sparseInterface.pointerName()}?.getPointer(0)").build()

    val vtblPtrSpec = PropertySpec.builder(sparseInterface.vtblName(), Pointer::class.asClassName().copy(true))
        .getter(getterSpec)
        .build()

    addProperty(vtblPtrSpec)
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding.runtime", "handleToString")
    addImport("com.github.knk190001.winrtbinding.runtime", "toHandle")
    addImport("com.github.knk190001.winrtbinding.runtime", "makeOutArray")
    addImport("com.github.knk190001.winrtbinding.runtime", "makePrimitiveOutArray")
    addImport("com.github.knk190001.winrtbinding.runtime", "invokeHR")
    addImport("com.github.knk190001.winrtbinding.runtime", "castToImpl")
    addImport("com.github.knk190001.winrtbinding.runtime", "getValue")
    addImport("com.github.knk190001.winrtbinding.runtime.interfaces", "getValue")
}

