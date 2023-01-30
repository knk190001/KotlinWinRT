package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.DirectProjectable
import com.github.knk190001.winrtbinding.generator.model.entities.SparseDelegate
import com.github.knk190001.winrtbinding.generator.model.entities.SparseGenericParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.ByReference
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

fun generateDelegate(
    sd: SparseDelegate,
    lookUpTypeReference: LookUp,
    projectType: (DirectProjectable<*>, List<SparseGenericParameter>) -> Unit
) = FileSpec.builder(sd.namespace, sd.name).apply {
    addImport("com.github.knk190001.winrtbinding.interfaces", "getValue")

    generateProjections(sd, lookUpTypeReference, projectType)
    val delegateTypeName = ClassName(sd.namespace, sd.name)
    val delegateParameters = sd.parameters.map {
        ParameterSpec.builder(it.name, it.type.asClassName()).build()
    }
    val delegateBodyTypeName = LambdaTypeName.get(delegateTypeName, delegateParameters, sd.returnType.asClassName())

    val superClassName = ClassName("com.github.knk190001.winrtbinding.interfaces", "Delegate")
        .parameterizedBy(delegateTypeName.nestedClass("Native"))

    val delegateBodySpec = TypeAliasSpec.builder("${sd.name}Body", delegateBodyTypeName).build()
    addTypeAlias(delegateBodySpec)

    val delegateClass = TypeSpec.classBuilder(sd.name).apply {
        superclass(superClassName)
        generateConstructor()
        generateCompanion(sd, delegateTypeName)
        generateNativeInterface(sd)
        generateByReference(sd)
        generateInvokeFunction(sd)
        generateABI(sd)
    }.build()
    addType(delegateClass)
}.build()

fun TypeSpec.Builder.generateInvokeFunction(sd: SparseDelegate) {
    val invokeFn = FunSpec.builder("invoke").apply {
        sd.parameters.forEach {
            addParameter(it.name,it.type.asClassName())
        }
        val cb = CodeBlock.builder().apply {
            if (sd.returnType.name != "Void") {
                addStatement("val result = %T()",sd.returnType.byReferenceClassName())
            }
            val marshalledParameters = sd.parameters.map {
                Marshaller.marshals.getOrDefault(it.type.asKClass(), Marshaller.default)
                    .generateToNativeMarshalCode(it.name)
            }
            marshalledParameters.forEach {
                add(it.second)
            }

            val marshalledNames = marshalledParameters.map {
                it.first
            }
            add("delegateStruct.fn!!.invoke(this.pointer,")
            add(marshalledNames.joinToString())
            if (sd.returnType.name != "Void") {
                add("result")
            }
            addStatement(")")

            if (sd.returnType.name != "Void") {
                addStatement("val resultValue = result.getValue()")
                val (name, cb) = Marshaller.marshals.getOrDefault(sd.returnType.asKClass(), Marshaller.default)
                    .generateFromNativeMarshalCode("resultValue")
                add(cb)
                addStatement("return $name")
            }
        }.build()
        addCode(cb)
    }.build()
    addFunction(invokeFn)
}

private fun TypeSpec.Builder.generateConstructor() {
    val ctor = FunSpec.constructorBuilder().apply {
        val ptrParameter = ParameterSpec.builder("ptr", Pointer::class.asClassName().copy(true))
            .defaultValue("%T.NULL", Pointer::class)
            .build()

        addParameter(ptrParameter)
    }.build()
    primaryConstructor(ctor)
    addSuperclassConstructorParameter("ptr")
}

private fun TypeSpec.Builder.generateCompanion(
    sd: SparseDelegate,
    delegateTypeName: ClassName
) {
    val companionObj = TypeSpec.companionObjectBuilder().apply {
        val createFn = FunSpec.builder("create").apply {
            addParameter("fn", ClassName(sd.namespace, "${sd.name}Body"))
            returns(delegateTypeName)

            val cb = CodeBlock.builder().apply {
                beginControlFlow("val nativeFn = Native { ")
                indent()
                addStatement("thisPtr: %T,", Pointer::class)
                sd.parameters.forEach {
                    addStatement("${it.name}: %T,", it.type.asClassName())
                }
                if (sd.returnType.name != "Void") {
                    addStatement("retVal: %T -> ", sd.returnType.asClassName())
                } else {
                    addStatement(" ->")
                }
                unindent()
                addStatement("val thisObj = %T()", delegateTypeName)
                val marshalledNames = sd.parameters.map {
                    val marshalResult = Marshaller.marshals.getOrDefault(it.type.asKClass(), Marshaller.default)
                        .generateFromNativeMarshalCode(it.name)
                    add(marshalResult.second)
                    marshalResult.first
                }
                if (sd.returnType.name != "Void") {
                    add("val result = fn(thisObj, ")
                    add(marshalledNames.joinToString())
                    add(")\n")
                    val marshalledReturnValue =
                        Marshaller.marshals.getOrDefault(sd.returnType.asKClass(), Marshaller.default)
                            .generateToNativeMarshalCode("result")
                    add(marshalledReturnValue.second)
                    addStatement("retVal.setValue(${marshalledReturnValue.first})")
                } else {
                    add("fn(thisObj, ")
                    add(marshalledNames.joinToString())
                    add(")\n")
                }
                addStatement("%T(0)", HRESULT::class)

                endControlFlow()
                addStatement("val newDelegate = %T()", delegateTypeName)
                addStatement("newDelegate.init(emptyList(), nativeFn)")
                addStatement("return newDelegate")
            }.build()
            addCode(cb)
        }.build()
        addFunction(createFn)

    }.build()
    addType(companionObj)
}

private fun TypeSpec.Builder.generateNativeInterface(sd: SparseDelegate) {
    val nativeInterface = TypeSpec.funInterfaceBuilder("Native").apply {
        addSuperinterface(StdCallCallback::class)
        val invoke = FunSpec.builder("invoke").apply {
            addParameter("thisPtr", Pointer::class)
            addModifiers(KModifier.ABSTRACT)
            sd.parameters.forEach {
                addParameter(it.name, it.type.asClassName())
            }
            if (sd.returnType.name != "Void") {
                addParameter("retVal", sd.returnType.byReferenceClassName())
            }
            returns(HRESULT::class)
        }.build()
        addFunction(invoke)
    }.build()
    addType(nativeInterface)
}

private fun generateProjections(
    sd: SparseDelegate,
    lookUpTypeReference: LookUp,
    projectType: (DirectProjectable<*>, List<SparseGenericParameter>) -> Unit
) {
    sd.parameters.forEach {
        if (it.type.genericParameters != null) {
            projectType(lookUpTypeReference(it.type) as DirectProjectable<*>, it.type.genericParameters)
        }
    }
    if (sd.returnType.genericParameters != null) {
        projectType(lookUpTypeReference(sd.returnType) as DirectProjectable<*>, sd.returnType.genericParameters)
    }
}

private fun TypeSpec.Builder.generateByReference(sd: SparseDelegate) {
    val delegateTypeName = ClassName(sd.namespace, sd.name)
    val byReference = TypeSpec.classBuilder("ByReference").apply {
        superclass(ByReference::class)
        val memberName = MemberName(Native::class.asClassName(), "POINTER_SIZE")
        addSuperclassConstructorParameter("%M", memberName)
        val getValueSpec = FunSpec.builder("getValue").apply {
            addCode("%T(pointer.getPointer(0))", delegateTypeName)
        }.build()
        addFunction(getValueSpec)

        val setValueSpec = FunSpec.builder("setValue").apply {
            addParameter("delegate", delegateTypeName)
            addCode("pointer.setPointer(0, delegate.pointer)")
        }.build()
        addFunction(setValueSpec)
    }.build()
    addType(byReference)
}

private fun TypeSpec.Builder.generateABI(sd: SparseDelegate) {
    val abiObject = TypeSpec.objectBuilder("ABI").apply {
        val iidProperty = PropertySpec.builder("IID", Guid.IID::class).apply {
            initializer("%T(%S)", Guid.IID::class,sd.guid)
        }.build()
        addProperty(iidProperty)
    }.build()
    addType(abiObject)
}