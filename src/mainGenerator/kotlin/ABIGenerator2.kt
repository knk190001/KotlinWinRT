import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.squareup.kotlinpoet.*
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinDef.UINTByReference
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.ByReference
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.heartpattern.gcg.api.Generator
import io.heartpattern.gcg.api.kotlin.KotlinCodeGenerator
import model.*
import java.io.InvalidObjectException
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream
import kotlin.reflect.KClass

@Generator
class ABIGenerator2 : KotlinCodeGenerator {
    override fun generateKotlin(): Collection<FileSpec> {
        val jsonObjects = mutableListOf<JsonObject>()
        println(System.getProperty("user.dir"))
        Path("${System.getProperty("user.dir")}/json2").forEachDirectoryEntry {
            jsonObjects.add(Parser.default().parse(it.inputStream()) as JsonObject)
        }

        val entities = jsonObjects.map {
            println(it["Name"])
            Klaxon().parse<SparseEntity>(it.toJsonString())
        }.filterNotNull()

        val interfaces = entities.filterIsInstance<SparseInterface>()
        lateinit var lookUpInterface: (SparseTypeReference) -> SparseInterface

        lookUpInterface = ret@{ typeReference: SparseTypeReference ->
            println(typeReference)
            val tr = if (typeReference.name.endsWith("&")) {
                typeReference.copy(name = typeReference.name.dropLast(1))
            }else{
                typeReference
            }
            return@ret interfaces.first {
                it.name == tr.name && it.namespace == tr.namespace
            }
        }

        val projections = mutableListOf<Pair<SparseInterface, Collection<SparseGenericParameter>>>()
        val projectInterface: (SparseInterface, Collection<SparseGenericParameter>) -> Unit =
            { sInterface: SparseInterface, genericParameters: Collection<SparseGenericParameter> ->
                if (projections.none {
                        isProjectionEquivalent(it, sInterface to genericParameters)
                    }) {
                    projections.add(sInterface to genericParameters)
                }

            }

        return entities.filter {
            if (it.type == "Interface") {
                if ((it as SparseInterface).genericParameters != null) {
                    return@filter false
                }
            }
            true
        }.map {
            when (it) {
                is SparseClass -> generateClass(it, lookUpInterface, projectInterface)
                is SparseInterface -> generateInterface(it, lookUpInterface, projectInterface)
                else -> throw InvalidObjectException("Object is not of type sparse class or sparse interface.")
            }
        }.toMutableList().apply {
            addAll(generateProjectedTypes(projections, lookUpInterface){ sInterface, parameters ->
                projections.contains(sInterface to parameters)
            })
        }
    }

    private fun isProjectionEquivalent(
        a: Pair<SparseInterface, Collection<SparseGenericParameter>>,
        b: Pair<SparseInterface, Collection<SparseGenericParameter>>
    ): Boolean {
        return a.first.namespace == b.first.namespace &&
                a.first.name == b.first.name &&
                equalsIgnoreOrder(a.second, b.second)
    }


    private fun generateProjectedTypes(
        projections: Collection<Pair<SparseInterface, Collection<SparseGenericParameter>>>,
        lookUpTypeReference: (SparseTypeReference) -> SparseInterface,
        checkIfExists: (SparseInterface, Collection<SparseGenericParameter>) ->  Boolean
    ): List<FileSpec> {
        val secondaryProjections = mutableListOf<Pair<SparseInterface, Collection<SparseGenericParameter>>>()
        return projections.map {
            val (sInterface, genericParameters) = it
            val suffix = getProjectedName(genericParameters)
            val newName = "${sInterface.name.substring(0, sInterface.name.length - 2)}$suffix"
            val projectedInterface = genericParameters.fold(sInterface) { acc, sparseGenericParameter ->
                acc.projectType(sparseGenericParameter.name, sparseGenericParameter.type!!)
            }.copy(name = newName)

            generateInterface(
                projectedInterface,
                lookUpTypeReference
            ) { secondaryProjectionInterface, parameters ->
                if (!checkIfExists(secondaryProjectionInterface, parameters)) {
                    secondaryProjections.add(secondaryProjectionInterface to parameters)
                }
            }
        }.toMutableList().apply {
            if (secondaryProjections.isNotEmpty()) {
                addAll(generateProjectedTypes(secondaryProjections.distinct(), lookUpTypeReference){ sInterface, params ->
                    if (secondaryProjections.contains(sInterface to params)) true
                    else checkIfExists(sInterface, params)
                })
            }
        }
    }

    private fun generateClass(
        sparseClass: SparseClass,
        lookUpTypeReference: (SparseTypeReference) -> SparseInterface,
        projectInterface: (SparseInterface, Collection<SparseGenericParameter>) -> Unit
    ): FileSpec {
        val fileSpec = FileSpec.builder(sparseClass.namespace, sparseClass.name)
        val typeSpec = TypeSpec.classBuilder(sparseClass.name)

        typeSpec.superclass(PointerType::class)
        val byRef = TypeSpec.classBuilder("ByReference")
        byRef.superclass(ByReference::class)
        val ptrSize = MemberName(Native::class.java.name, "POINTER_SIZE")
        byRef.addSuperclassConstructorParameter("%M", ptrSize)

        val getValueSpec = FunSpec.builder("getValue")
        getValueSpec.returns(ClassName("", sparseClass.name))

        val getValueCode = CodeBlock.builder().apply {
            addStatement("return %T(pointer.getPointer(0))", ClassName("", sparseClass.name))
        }.build()
        getValueSpec.addCode(getValueCode)

        byRef.addFunction(getValueSpec.build())

        val setValueSpec = FunSpec.builder("setValue")
        setValueSpec.addParameter("value", ClassName("", sparseClass.name))

        val setValueCode = CodeBlock.builder().apply {
            addStatement("pointer.setPointer(0, value.pointer)")
        }.build()
        setValueSpec.addCode(setValueCode)

        byRef.addFunction(setValueSpec.build())

        val constructorSpec = FunSpec.constructorBuilder()
        val ptrParameterSpec = ParameterSpec.builder("ptr", Pointer::class)
            .defaultValue("Pointer.NULL")
        constructorSpec.addParameter(ptrParameterSpec.build())
        constructorSpec.addModifiers(KModifier.PRIVATE)
        typeSpec.primaryConstructor(constructorSpec.build())
        typeSpec.addSuperclassConstructorParameter("ptr")

        sparseClass.interfaces.filter {
            it.name.contains('`') && it.genericParameters != null
        }.forEach {
            projectInterface(lookUpTypeReference(it), it.genericParameters!!)
        }

        sparseClass.interfaces.filter {
            !it.name.contains('`')
        }.map {
            FunSpec.builder("as${it.name}").apply {
                addModifiers(KModifier.PRIVATE)
                val cb = CodeBlock.builder().apply {
                    addStatement(
                        "val refiid = %T(%T.IID)",
                        REFIID::class,
                        ClassName(it.namespace, it.name)
                    )
                    addStatement("val ref = %T()", PointerByReference::class.asClassName())
                    addStatement(
                        "%T(pointer.getPointer(0)).queryInterface(pointer, refiid, ref)",
                        ClassName("", "IUnknown")
                    )
                    addStatement("return %T(ref.value)", ClassName(it.namespace, it.name))
                }.build()
                addCode(cb)

                returns(ClassName(it.namespace, it.name))
            }.build()
        }.forEach(typeSpec::addFunction)

        sparseClass.interfaces.filter {
            !it.name.contains('`')
        }.map {
            PropertySpec.builder("${it.name}_Interface", ClassName(it.namespace, it.name)).apply {
                val delegateCb = CodeBlock.builder().apply {
                    addStatement("lazy {")
                    indent()
                    addStatement("as${it.name}()")
                    unindent()
                    addStatement("}")
                }.build()
                delegate(delegateCb)
            }.build()
        }.forEach(typeSpec::addProperty)
        typeSpec.addType(generateFromABIObject(sparseClass))
        sparseClass.interfaces.flatMap {
            generateMethodsForClassFromInterface(it, lookUpTypeReference, projectInterface)
        }.forEach(typeSpec::addFunction)
        typeSpec.addType(byRef.build())
        fileSpec.addImport("", "toHandle")
        fileSpec.addImport("", "checkHR")
        if (sparseClass.traits.any {
                it.traitType == "DirectActivation"
            }) {
            typeSpec.addFunction(generateDirectActivationConstructor(sparseClass))
        }

        fileSpec.addType(typeSpec.build())

        return fileSpec.build()
    }

    private fun generateDirectActivationConstructor(sClass: SparseClass): FunSpec {
        return FunSpec.constructorBuilder().apply {
            callThisConstructor(CodeBlock.of("ABI.activate()"))
        }.build()
    }

    private fun isMethodValid(method: SparseMethod): Boolean {
        return method.parameters.none { it.type.name.contains('[') } &&
                !method.returnType.name.contains('[')
    }

    private fun generateDirectActivator(sClass: SparseClass): FunSpec {
        return FunSpec.builder("activate").apply {
            this.returns(Pointer::class)
            val cb = CodeBlock.builder().apply {
                addStatement(
                    "val refiid = %T(%M)",
                    REFIID::class,
                    MemberName(ClassName("", "IActivationFactory"), "IID")
                )
                addStatement("val iAFPtr = %T()", PointerByReference::class)
                val win32 = MemberName(ClassName("", "JNAApiInterface"), "INSTANCE")
                val classpath = "${sClass.namespace}.${sClass.name}"
                addStatement("var hr = %M.RoGetActivationFactory(%S.toHandle(),refiid,iAFPtr)", win32, classpath)
                addStatement("checkHR(hr)")
                addStatement("val iAF = %T(iAFPtr.value.getPointer(0))", ClassName("", "IActivationFactory"))
                addStatement("val result = %T()", PointerByReference::class)
                addStatement("hr = iAF.activateInstance(iAFPtr.value, result)")
                addStatement("checkHR(hr)")
                addStatement("return result.value")
            }.build()
            addCode(cb)
        }.build()
    }

    private fun generateFromABIObject(sClass: SparseClass): TypeSpec {
        return TypeSpec.objectBuilder("ABI").apply {
            val fromABISpec = FunSpec.builder("fromABI").apply {
                addParameter("ptr", Pointer::class)
                returns(ClassName("", sClass.name))
                val cb = CodeBlock.builder().apply {
                    addStatement("return %T(ptr)", ClassName("", sClass.name))
                }.build()
                addCode(cb)
            }.build()
            addFunction(fromABISpec)
            if (sClass.traits.any {
                    it.traitType == "DirectActivation"
                }) {
                addFunction(generateDirectActivator(sClass))
            }
        }.build()
    }

    private fun generateMethodsForClassFromInterface(
        typeReference: SparseTypeReference,
        lookUpTypeReference: (SparseTypeReference) -> SparseInterface,
        projectInterface: (SparseInterface, Collection<SparseGenericParameter>) -> Unit,
    ): Collection<FunSpec> {
        val sInterface = lookUpTypeReference(typeReference)
        if (sInterface.genericParameters != null) return emptyList()
        return sInterface.methods.filter(::isMethodValid).map { method ->
            FunSpec.builder(method.name).apply {
                method.parameters.forEach {
                    addParameter(it.name, it.type.asClassName())
                    if (it.type.genericParameters != null) {
                        projectInterface(lookUpTypeReference(it.type), it.type.genericParameters)
                    }
                }
                if (method.returnType.genericParameters != null) {
                    projectInterface(lookUpTypeReference(method.returnType), method.returnType.genericParameters)
                }
                returns(method.returnType.asClassName())
                val cb = CodeBlock.builder().apply {
                    add("return ${sInterface.name}_Interface.${method.name}(")
                    method.parameters.forEach {
                        add("${it.name}, ")
                    }
                    add(")")
                }.build()

                addCode(cb)
            }.build()
        }.toList()
    }

    private fun generateInterface(
        sparseInterface: SparseInterface,
        lookUpTypeReference: (SparseTypeReference) -> SparseInterface,
        projectInterface: (SparseInterface, Collection<SparseGenericParameter>) -> Unit
    ): FileSpec {
        val fileSpec = FileSpec.builder(sparseInterface.namespace, sparseInterface.name)
        fileSpec.addImport("", "handleToString")
        fileSpec.addImport("", "toHandle")
        val typeSpec = TypeSpec.classBuilder(sparseInterface.name)

        typeSpec.superclass(PointerType::class)
        val byRef = TypeSpec.classBuilder("ByReference")
        byRef.superclass(ByReference::class)
        val ptrSize = MemberName(Native::class.java.name, "POINTER_SIZE")
        byRef.addSuperclassConstructorParameter("%M", ptrSize)

        val getValueSpec = FunSpec.builder("getValue")
        getValueSpec.returns(ClassName("", sparseInterface.name))

        val getValueCode = CodeBlock.builder().apply {
            addStatement("return %T(pointer.getPointer(0))", ClassName("", sparseInterface.name))
        }.build()
        getValueSpec.addCode(getValueCode)

        byRef.addFunction(getValueSpec.build())

        val setValueSpec = FunSpec.builder("setValue")
        setValueSpec.addParameter("value", ClassName("", sparseInterface.name))

        val setValueCode = CodeBlock.builder().apply {
            addStatement("pointer.setPointer(0, value.pointer)")
        }.build()
        setValueSpec.addCode(setValueCode)

        byRef.addFunction(setValueSpec.build())

        val vtblPtrSpec = PropertySpec.builder("vtblPtr", Pointer::class)
            .getter(FunSpec.getterBuilder().addCode("return pointer.getPointer(0)").build()).build()

        typeSpec.addProperty(vtblPtrSpec)

        val companionObjectSpec = TypeSpec.companionObjectBuilder().apply {
            val iidSpec = PropertySpec.builder("IID", IID::class.asClassName()).apply {
                this.initializer("%T(%S)", IID::class.asClassName(), sparseInterface.guid)
            }
            addProperty(iidSpec.build())
        }.build()

        typeSpec.addType(companionObjectSpec)

        val constructorSpec = FunSpec.constructorBuilder()
        val ptrParameterSpec = ParameterSpec.builder("ptr", Pointer::class)
            .defaultValue("Pointer.NULL")
        constructorSpec.addParameter(ptrParameterSpec.build())
        typeSpec.primaryConstructor(constructorSpec.build())
        typeSpec.addSuperclassConstructorParameter("ptr")


        sparseInterface.methods.mapIndexed { i, it ->
            val builder = FunSpec.builder(it.name)
            it.parameters.forEach { param ->
                builder.addParameter(param.name, param.type.asClassName())
                if (param.type.genericParameters != null) {
                    projectInterface(lookUpTypeReference(param.type), param.type.genericParameters)
                }
            }

            if (it.returnType.genericParameters != null) {
                projectInterface(lookUpTypeReference(it.returnType), it.returnType.genericParameters)
            }

            val stdConventionMember = MemberName(Function::class.asClassName(), "ALT_CONVENTION")
            val code = CodeBlock.builder().apply {
                addStatement(
                    "val fnPtr = vtblPtr.getPointer(${i + 6}L * %M)",
                    MemberName(Native::class.java.name, "POINTER_SIZE")
                )
                addStatement("val fn = %T.getFunction(fnPtr, %M)", Function::class.asClassName(), stdConventionMember)

                val marshaledNames = it.parameters.map {
                    val marshalResult =
                        Marshaller.marshals.getOrDefault(it.type.asKClass(), Marshaller.default)
                            .generateToNativeMarshalCode(it.name)
                    add(marshalResult.second)
                    marshalResult.first
                }

                if (it.returnType.name != "Void") {
                    addStatement("val result = %T()", it.returnType.byReferenceClassName())
                }
                add("val hr = %T(fn.invokeInt(arrayOf(pointer, ", HRESULT::class.asClassName())
                marshaledNames.forEach {
                    add("${it}, ")
                }
                if (it.returnType.name != "Void") {
                    add("result)))\n")
                } else {
                    add(")))\n")
                }

                addStatement("if (hr.toInt() != 0) {")
                indent()
                addStatement("throw %T(hr.toString())", RuntimeException::class.asClassName())
                unindent()
                addStatement("}")
                val returnMarshaller = Marshaller.marshals.getOrDefault(it.returnType.asKClass(), Marshaller.default)
                val (unmarshalledName, unmarshallingCode) = returnMarshaller.generateFromNativeMarshalCode("resultValue")

                if (it.returnType.name != "Void") {
                    addStatement("val resultValue = result.getValue()")
                    add(unmarshallingCode)
                    addStatement("return $unmarshalledName")
                }
            }

            builder.addCode(code.build())
            builder.returns(it.returnType.asClassName())
            if (isMethodValid(it)) builder.build() else null
        }.filterNotNull().forEach(typeSpec::addFunction)

        typeSpec.addType(byRef.build())
        fileSpec.addType(typeSpec.build())

        return fileSpec.build()
    }
}


fun SparseTypeReference.asClassName(): ClassName {
    if (namespace == "System") {
        return when (name) {
            "UInt32" -> UINT::class.asClassName()
            "Double" -> Double::class.asClassName()
            "Boolean" -> Boolean::class.asClassName()
            "Int32" -> Int::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "String" -> String::class.asClassName()
            "UInt32&" -> UINTByReference::class.asClassName()
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (this.name.endsWith("&")) {
        if (genericParameters != null) {
            val suffix = getProjectedName(genericParameters)
            val name = "${this.name.substring(0, this.name.length - 3)}$suffix"
            return ClassName("${this.namespace}.$name", "ByReference")
        }
        return ClassName("${this.namespace}.${name.subSequence(0, name.lastIndex)}", "ByReference")
    }
    if (genericParameters != null) {
        val suffix = getProjectedName(genericParameters)
        val name = "${this.name.substring(0, this.name.length - 2)}$suffix"
        return ClassName(this.namespace, name)
    }
    return ClassName(this.namespace, this.name)
}

fun SparseTypeReference.asKClass(): KClass<*> {
    if (namespace == "System") {
        return when (name) {
            "UInt32" -> UINT::class
            "Double" -> Double::class
            "Boolean" -> Boolean::class
            "Int32" -> Int::class
            "Void" -> Unit::class
            "String" -> String::class
            "UInt32&" -> UINTByReference::class
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    return Nothing::class
}

private fun getProjectedName(parameter: SparseGenericParameter): String {
    if (parameter.type!!.genericParameters == null) return parameter.type.name
    val nameWithoutIllegalSymbol = parameter.type.name.substring(0, parameter.type.name.length - 2)
    return "$nameWithoutIllegalSymbol${getProjectedName(parameter.type.genericParameters!!)}";
}

private val separator = "_"
private fun getProjectedName(parameters: Collection<SparseGenericParameter>): String {
    val suffix = parameters.fold(separator) { acc, sparseGenericParameter ->
        "$acc${getProjectedName(sparseGenericParameter)}$separator"
    }
    return suffix
}

fun SparseTypeReference.byReferenceClassName(): ClassName {
    if (namespace == "System") {
        return when (name) {
            "UInt32" -> UINTByReference::class.asClassName()
            "Double" -> DoubleByReference::class.asClassName()
            "Boolean" -> ByteByReference::class.asClassName()
            "Int32" -> IntByReference::class.asClassName()
            "Void" -> Unit::class.asClassName()
            "String" -> HANDLEByReference::class.asClassName()
            else -> throw NotImplementedError("Type: $namespace.$name is not handled")
        }
    }
    if (genericParameters != null) {
        val suffix = getProjectedName(genericParameters)
        val name = "${this.name.substring(0, this.name.length - 2)}$suffix"
        return ClassName("${this.namespace}.$name", "ByReference")
    }

    return ClassName(this.namespace + ".${this.name}", "ByReference")
}

fun <T> equalsIgnoreOrder(list1: Collection<T>, list2: Collection<T>) =
    list1.size == list2.size && list1.toSet() == list2.toSet()
