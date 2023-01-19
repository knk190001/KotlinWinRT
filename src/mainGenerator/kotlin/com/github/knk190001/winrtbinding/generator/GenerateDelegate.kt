package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.DirectProjectable
import com.github.knk190001.winrtbinding.generator.model.entities.SparseDelegate
import com.github.knk190001.winrtbinding.generator.model.entities.SparseGenericParameter
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

fun generateDelegate(
    sparseDelegate: SparseDelegate,
    lookUpTypeReference: LookUpFn,
    projectInterface: (DirectProjectable<*>, Collection<SparseGenericParameter>) -> Unit
) =
    FileSpec.builder(sparseDelegate.namespace, sparseDelegate.name).apply {
        val delegateClass = ClassName("com.github.knk190001.winrtbinding.interfaces", "Delegate")
        val nativeDelegateClass = ClassName(sparseDelegate.namespace, "${sparseDelegate.name}_Native")
        val parameterizedDelegate = delegateClass.parameterizedBy(nativeDelegateClass)
        val typeAlias = TypeAliasSpec.builder(sparseDelegate.name, parameterizedDelegate).build()
        addTypeAlias(typeAlias)

        val funInterface = TypeSpec.funInterfaceBuilder(nativeDelegateClass).apply {
            addSuperinterface(StdCallCallback::class)
            val invoke = FunSpec.builder("invoke").apply {
                addModifiers(KModifier.ABSTRACT)
                addParameter("thisPtr", Pointer::class)
                sparseDelegate.parameters.forEach {
                    addParameter(it.name, it.type.asClassName())
                }
                returns(sparseDelegate.returnType.byReferenceClassName())
            }.build()
            addFunction(invoke)
        }.build()
        addType(funInterface)

        val invokeExtension = FunSpec.builder("invoke").apply {
            addModifiers(KModifier.OPERATOR)
            receiver(parameterizedDelegate)

            sparseDelegate.parameters.forEach {
                addParameter(it.name, it.type.asClassName())
                if (it.type.genericParameters != null) {
                    projectInterface(
                        lookUpTypeReference(it.type) as DirectProjectable<*>,
                        it.type.genericParameters
                    )
                }
            }
            if (sparseDelegate.returnType.genericParameters != null) {
                projectInterface(
                    lookUpTypeReference(sparseDelegate.returnType) as DirectProjectable<*>,
                    sparseDelegate.returnType.genericParameters
                )
            }


            returns(sparseDelegate.returnType.asClassName())

            val cb = CodeBlock.builder().apply {
                if (sparseDelegate.returnType.name != "Void") {
                    addStatement("val returnValue = %T()", sparseDelegate.returnType.byReferenceClassName())
                }

                val marshaledNames = sparseDelegate.parameters.map {
                    val marshalResult = Marshaller.marshals.getOrDefault(it.type.asKClass(), Marshaller.default)
                            .generateToNativeMarshalCode(it.name)
                    add(marshalResult.second)
                    marshalResult.first
                }
                add("val hr = this.delegateStruct.fn?.invoke(this.pointer,")
                add(marshaledNames.joinToString())
                if (sparseDelegate.returnType.name != "Void") {
                    add(", returnValue")
                }
                add(")")

                val returnMarshaller = Marshaller.marshals.getOrDefault(sparseDelegate.returnType.asKClass(), Marshaller.default)
                val (unmarshalledName, unmarshallingCode) = returnMarshaller.generateFromNativeMarshalCode("resultValue")

                if (sparseDelegate.returnType.name != "Void") {
                    addStatement("val resultValue = result.getValue()")
                    add(unmarshallingCode)
                    addStatement("return $unmarshalledName")
                }
            }.build()
            addCode(cb)
        }

    }.build()
