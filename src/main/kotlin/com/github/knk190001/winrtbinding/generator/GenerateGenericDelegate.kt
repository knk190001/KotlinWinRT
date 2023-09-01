@file:Suppress("DuplicatedCode")

package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.github.knk190001.winrtbinding.runtime.*
import com.github.knk190001.winrtbinding.runtime.interfaces.Delegate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.ByReference
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance

fun generateGenericDelegate(sparseDelegate: SparseDelegate): FileSpec {
    return FileSpec.builder(sparseDelegate.namespace, sparseDelegate.name).apply {
        addImports()
        addDelegateBodyTypeAlias(sparseDelegate)
        val delegateTypeSpec = TypeSpec.classBuilder(sparseDelegate.name).apply {
            superclass(Delegate::class.parameterizedBy(StdCallCallback::class))
            addDelegateTypeSpec(sparseDelegate)
        }.build()
        addType(delegateTypeSpec)
    }.build()
}

private fun FileSpec.Builder.addImports() {
    addImport("com.github.knk190001.winrtbinding.runtime","guidOf")
    addImport("com.github.knk190001.winrtbinding.runtime","invokeHR")
    addImport("com.github.knk190001.winrtbinding.runtime","checkHR")
    addImport("com.github.knk190001.winrtbinding.runtime","makeByReferenceType")
    addImport("com.github.knk190001.winrtbinding.runtime","marshalFromNative")
    addImport("com.github.knk190001.winrtbinding.runtime","marshalToNative")
    addImport("com.github.knk190001.winrtbinding.runtime", "iUnknownIID")

    addImport("kotlin.reflect","typeOf")
    addImport("com.sun.jna","Memory")
}

private fun TypeSpec.Builder.addDelegateTypeSpec(sparseDelegate: SparseDelegate) {
    addDelegateAnnotations(sparseDelegate)
    addTypeParameters(sparseDelegate)
    addPrimaryConstructor(sparseDelegate)
    addInvokeOperator(sparseDelegate)
    addCompanionObject(sparseDelegate)
    addByReferenceType(sparseDelegate)
}
private fun TypeSpec.Builder.addByReferenceType(sparseDelegate: SparseDelegate) {
    val byReferenceInterfaceSpec = TypeSpec.classBuilder("ByReference").apply {
        addTypeParameters(sparseDelegate)
        superclass(ByReference::class)
        addSuperclassConstructorParameter("%M", Native::class.member("POINTER_SIZE"))
        addSuperinterface(
            IByReference::class.asClassName()
                .parameterizedBy(sparseDelegate.asTypeReference().asGenericTypeParameter())
        )

        if (sparseDelegate.genericParameters != null) {
            addSuperinterface(ISpecializable::class)

            val typePropertySpec = PropertySpec.builder("type", KType::class.asTypeName().copy(nullable = true)).apply {
                mutable(true)
                initializer("null")
            }.build()

            addProperty(typePropertySpec)

            val specializeSpec = FunSpec.builder("specialize").apply {
                addModifiers(KModifier.OVERRIDE)
                addParameter("type", KType::class)
                addStatement("this.type = type")
            }.build()

            addFunction(specializeSpec)
        }

        val getValueFn = FunSpec.builder("getValue").apply {
            addModifiers(KModifier.OVERRIDE)
            addCode(
                "return %T(type!!, pointer.getPointer(0))",
                sparseDelegate.asTypeReference().asGenericTypeParameter()
            )
            returns(sparseDelegate.asTypeReference().asGenericTypeParameter())
        }.build()
        addFunction(getValueFn)

        val setValueFn = FunSpec.builder("setValue").apply {
            addParameter("value", sparseDelegate.asTypeReference().asGenericTypeParameter())
            addCode("pointer = value.pointer!!",)
        }.build()
        addFunction(setValueFn)

    }.build()

    addType(byReferenceInterfaceSpec)
}

private fun TypeSpec.Builder.addCompanionObject(sparseDelegate: SparseDelegate) {
    val companionObject = TypeSpec.companionObjectBuilder().apply {
        addBodyInvokeOperator(sparseDelegate)
        addPointerInvokeOperator(sparseDelegate)
        addReifiedPointerInvokeOperator(sparseDelegate)
    }.build()
    addType(companionObject)
}

private fun TypeSpec.Builder.addReifiedPointerInvokeOperator(sparseDelegate: SparseDelegate) {
    val invokeFn = FunSpec.builder("invoke").apply {
        addModifiers(KModifier.OPERATOR, KModifier.INLINE)
        addMethodTypeParameters(sparseDelegate)
        addParameter("pointer", Pointer::class.asClassName().copy(true))
        returns(sparseDelegate.asTypeReference().asGenericTypeParameter())
        addStatement("val type = typeOf<%T>()", sparseDelegate.asTypeReference().asGenericTypeParameter())
        addStatement("return %T(type, pointer)", sparseDelegate.asTypeReference().asClassName())
    }.build()
    addFunction(invokeFn)
}

private fun TypeSpec.Builder.addPointerInvokeOperator(sparseDelegate: SparseDelegate) {
    val typeParameterIndexMap =
        sparseDelegate.genericParameters?.mapIndexed { idx, it -> it.name to idx }?.toMap()
            ?: emptyMap()
    val invokeFn = FunSpec.builder("invoke").apply {
        addModifiers(KModifier.OPERATOR)
        addMethodTypeParameters(sparseDelegate, reified = false)
        addParameter("type", KType::class)
        addParameter("ptr", Pointer::class.asClassName().copy(true))
        returns(sparseDelegate.asTypeReference().asGenericTypeParameter())
        val bodyClassName = getBodyClassName(sparseDelegate)
        val cb = CodeBlock.builder().apply {
            addStatement("val thisPtr = ptr!!.getPointer(0)")
            addStatement("val vtbl = thisPtr!!.getPointer(0)")
            addStatement("val function = %T.getFunction(vtbl.getPointer(3L * %T.POINTER_SIZE))", Function::class, Native::class)
            beginControlFlow("val body: %T =  ", bodyClassName)
            addStatement(sparseDelegate.parameters.joinToString(transform = SparseParameter::name, postfix = " ->"))
            val marshaledParameters = Marshaller.marshalVariablesToNative(sparseDelegate.parameters, this)
                .zip(sparseDelegate.parameters)
                .map { (marshaled, sparseParameter) ->
                    if (sparseParameter.type.namespace.isNotEmpty()){
                        marshaled
                    }else{
                        "marshalToNative(${marshaled}, ${kTypeStringFor(sparseParameter.type, typeParameterIndexMap)})"
                    }
                }

            val result = if(sparseDelegate.returnType.name != "Void") {
                addResultVariable(sparseDelegate)
                ", result"
            } else ""

            addStatement("val hr = function.invokeHR(arrayOf(this.pointer, ${marshaledParameters.joinToString(postfix = result)}))")
            addStatement("checkHR(hr)")

            if(sparseDelegate.returnType.name != "Void") {
                add("val returnType = ")
                kTypeStatementFor(sparseDelegate.returnType, typeParameterIndexMap)
                addStatement("marshalFromNative<%T>(result.getValue(), returnType)", sparseDelegate.returnType.asGenericTypeParameter())
            }
            endControlFlow()
            addStatement("return %T(type, body, thisPtr)", sparseDelegate.asTypeReference().asClassName())
        }.build()
        addCode(cb)
    }.build()
    addFunction(invokeFn)
}

private fun CodeBlock.Builder.addResultVariable(sparseDelegate: SparseDelegate) {
    val typeParameterIndexMap = sparseDelegate.genericParameters
        ?.mapIndexed { idx, it -> it.name to idx }
        ?.toMap() ?: emptyMap()


    val resultType = sparseDelegate.returnType
    if (!resultType.hasGenericParameter()) {
        if (resultType.isTypeParameter()) {
            addStatement(
                "val result = makeByReferenceType<%L>(%L!!.arguments[%L].type!!)",
                resultType.name,
                "type",
                typeParameterIndexMap[resultType.name]
            )
        } else {
            addStatement("val result = makeByReferenceType<%T>()", resultType.asClassName())
        }
        return
    }

    addStatement(
        "val result = makeByReferenceType<%T>(", resultType.asGenericTypeParameter()
    )
    kTypeStatementFor(resultType, typeParameterIndexMap)
    add(")\n")
}

private fun kTypeStringFor(
    typeReference: SparseTypeReference,
    typeParameterIndexMap: Map<String, Int>,
    projection: Boolean = false
): String {
    val builder = CodeBlock.builder()
    return with(builder) {
        kTypeStatementFor(typeReference, typeParameterIndexMap, projection, true)
        builder.build().toString()
    }
}

private fun CodeBlock.Builder.kTypeStatementFor(
    typeReference: SparseTypeReference,
    typeParameterIndexMap: Map<String, Int>,
    projection: Boolean = false,
    root: Boolean = false
) {

    if (typeReference.isTypeParameter()) {
        add("type!!.arguments[${typeParameterIndexMap[typeReference.name]}]")
        if (root) {
            add(".type!!")
        }
        return
    }
    if (projection) {
        add("%T(%M,", KTypeProjection::class, KVariance::class.member("INVARIANT"))
    }
    if (!typeReference.hasGenericParameter()) {
        add("%T::class.createType()", typeReference.asClassName())
        if (projection) {
            add(")")
        }
        return
    }

    val type = typeReference.copy(genericParameters = null).dropGenericParameterCount()

    add("%T::class.createType(listOf(\n", ClassName(type.namespace, type.name))
    indent()
    typeReference.genericParameters!!.forEach {
        if (it.type == null) {
            kTypeStatementFor(SparseTypeReference(it.name, ""), typeParameterIndexMap, projection)
        } else {
            kTypeStatementFor(it.type, typeParameterIndexMap, true)
        }
        add(",\n")
    }
    unindent()
    add("))")
    if (projection) {
        add(")")
    }

}


private fun TypeSpec.Builder.addBodyInvokeOperator(sparseDelegate: SparseDelegate) {
    val invokeFn = FunSpec.builder("invoke").apply {
        addModifiers(KModifier.OPERATOR, KModifier.INLINE)
        addMethodTypeParameters(sparseDelegate, true)

        val bodyClassName = getBodyClassName(sparseDelegate)
        val bodyParam = ParameterSpec.builder("fn", bodyClassName).apply {
            addModifiers(KModifier.NOINLINE)
        }.build()
        addParameter(bodyParam)

        val delegateType = sparseDelegate.asTypeReference().asGenericTypeParameter()
        returns(delegateType)

        val cb = CodeBlock.builder().apply {
            addStatement("val type = typeOf<%T>()", delegateType)
            addStatement("val newDelegate = %T(type, fn, Memory(12))", delegateType)
            addStatement("val nativeFn = %T.createNative<%T>(newDelegate)",DelegateFactory::class, delegateType)
            addStatement("newDelegate.init(listOf(guidOf<%T>(), iUnknownIID), nativeFn)", delegateType)
            addStatement("return newDelegate")
        }.build()
        addCode(cb)
    }.build()
    addFunction(invokeFn)
}

private fun parameterToTypeName(sparseParameter: SparseParameter): TypeName {
    return if (sparseParameter.type.namespace.isEmpty()) {
        TypeVariableName(sparseParameter.type.name)
    }else {
        sparseParameter.type.asGenericTypeParameter()
    }
}

private fun TypeSpec.Builder.addInvokeOperator(sparseDelegate: SparseDelegate) {
    val invokeFn = FunSpec.builder("invoke").apply {
        addModifiers(KModifier.OPERATOR)

        val parameterNames = sparseDelegate.parameters.map { it.name }

        sparseDelegate.parameters
            .map(::parameterToTypeName)
            .zip(parameterNames)
            .forEach { (type, name) ->
                addParameter(name, type)
            }

        if (sparseDelegate.returnType.name != "Void") {
            returns(sparseDelegate.returnType.asGenericTypeParameter())
        }

        val cb = CodeBlock.builder().apply {
            if (sparseDelegate.returnType.name != "Void") {
                add("return ")
            }
            addStatement("body!!(${parameterNames.joinToString()})")
        }.build()
        addCode(cb)
    }.build()
    addFunction(invokeFn)
}

private fun TypeSpec.Builder.addPrimaryConstructor(sparseDelegate: SparseDelegate) {
    val constructor = FunSpec.constructorBuilder().apply {
        val typeParameterSpec = ParameterSpec.builder("type", KType::class.asClassName().copy(true)).apply {
            this.defaultValue("null")
        }.build()
        addParameter(typeParameterSpec)

        val typePropertySpec = PropertySpec.builder("type", KType::class.asClassName().copy(true)).apply {
            this.initializer("type")
        }.build()
        addProperty(typePropertySpec)

        val bodyClassName = getBodyClassName(sparseDelegate).copy(true)
        val bodyParameterSpec = ParameterSpec.builder("body", bodyClassName).apply {
            this.defaultValue("null")
        }.build()
        addParameter(bodyParameterSpec)
        val bodyPropertySpec = PropertySpec.builder("body", bodyClassName).apply {
            this.initializer("body")
        }.build()
        addProperty(bodyPropertySpec)

        val ptrParameterSpec = ParameterSpec.builder("ptr", typeNameOf<Pointer?>()).apply {
            this.defaultValue("%T.NULL",Pointer::class)
        }.build()
        addParameter(ptrParameterSpec)
    }.build()
    primaryConstructor(constructor)
}

private fun getBodyClassName(sparseDelegate: SparseDelegate): ParameterizedTypeName {
    val parameterizedTypeName = sparseDelegate.asTypeReference().asGenericTypeParameter()
    return parameterizedTypeName(sparseDelegate, parameterizedTypeName)
}

private fun parameterizedTypeName(
    sparseDelegate: SparseDelegate,
    parameterizedTypeName: TypeName
): ParameterizedTypeName {
    val bodyClassName = ClassName(sparseDelegate.namespace, "${sparseDelegate.name}Body")
        .parameterizedBy(*(parameterizedTypeName as ParameterizedTypeName).typeArguments.toTypedArray())
    return bodyClassName
}

private fun TypeSpec.Builder.addTypeParameters(sparseDelegate: SparseDelegate) {
    sparseDelegate.genericParameters!!
        .map(SparseGenericParameter::name)
        .map(TypeVariableName::invoke)
        .forEach(::addTypeVariable)
}

private fun FunSpec.Builder.addMethodTypeParameters(sparseDelegate: SparseDelegate, reified: Boolean = true) {
    sparseDelegate.genericParameters!!
        .map(SparseGenericParameter::name)
        .map(TypeVariableName::invoke)
        .map {
            it.copy(reified = reified)
        }
        .forEach(::addTypeVariable)
}

private fun TypeSpec.Builder.addDelegateAnnotations(sparseDelegate: SparseDelegate) {
    addAnnotation(GenericType::class)
    addAnnotation(DynamicSignature::class)
    addGuidAnnotation(sparseDelegate.guid)
    addByReferenceAnnotation(sparseDelegate)
}

private fun TypeSpec.Builder.addByReferenceAnnotation(sparseDelegate: SparseDelegate) {
    val annotation = AnnotationSpec.builder(WinRTByReference::class).apply {
        addMember(
            "%T::class",
            (sparseDelegate.asTypeReference()
                .asClassName() as ClassName).nestedClass("ByReference")
        )
    }.build()
    addAnnotation(annotation)
}

private fun FileSpec.Builder.addDelegateBodyTypeAlias(sparseDelegate: SparseDelegate) {
    val delegateTypeName = ClassName(sparseDelegate.namespace, sparseDelegate.name)
    val delegateParameters = sparseDelegate.parameters.map {
        val type = if (it.type.isTypeParameter()) {
            TypeVariableName(it.type.name)
        } else {
            it.type.asGenericTypeParameter(false)
                .copy(!it.type.isPrimitiveSystemType() && !it.type.isArray)
        }
        ParameterSpec.builder(it.name, type).build()

    }
    val delegateTypeParameters = sparseDelegate.genericParameters!!
        .map(SparseGenericParameter::name)
        .map(TypeVariableName::invoke)

    val delegateBodyTypeName = LambdaTypeName.get(
        delegateTypeName.parameterizedBy(delegateTypeParameters),
        delegateParameters,
        sparseDelegate.returnType.asGenericTypeParameter(false)
    )

    val delegateBodyTypeAlias = TypeAliasSpec
        .builder("${sparseDelegate.name}Body", delegateBodyTypeName)
        .addTypeVariables(delegateTypeParameters)
        .build()
    addTypeAlias(delegateBodyTypeAlias)
}
