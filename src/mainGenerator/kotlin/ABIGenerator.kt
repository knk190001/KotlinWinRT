import com.beust.klaxon.JsonArray
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Parser
import com.squareup.kotlinpoet.*
import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import io.heartpattern.gcg.api.Generator
import io.heartpattern.gcg.api.kotlin.KotlinCodeGenerator
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream

@Generator
class ABIGenerator : KotlinCodeGenerator {
    override fun generateKotlin(): Collection<FileSpec> {
        val jsonObjects = mutableListOf<JsonObject>()
        println(System.getProperty("user.dir"))
        Path("${System.getProperty("user.dir")}/json").forEachDirectoryEntry {
            jsonObjects.add(Parser.default().parse(it.inputStream()) as JsonObject)
        }

        return jsonObjects.filter(::isInterface).map {
            it to FileSpec.builder("ABI.${it["Namespace"].toString()}", it["Name"].toString())
        }
            .map(::enrichFile)
            .map(Pair<JsonObject, FileSpec.Builder>::second)
            .map(FileSpec.Builder::build)
    }

}

fun enrichFile(winrtInterface: Pair<JsonObject, FileSpec.Builder>): Pair<JsonObject, FileSpec.Builder> {
    val (obj, fs) = winrtInterface
    val tsb = TypeSpec.classBuilder(obj["Name"].toString())
        .superclass(Structure::class.java)
        .addProperty(
            PropertySpec.builder("iInspectable", ClassName("", listOf("IInspectable")).copy(true))
                .addAnnotation(JvmField::class)
                .initializer("null")
                .mutable(true)
                .build()
        )

    val names = mutableSetOf<String>()
    (obj["Methods"] as JsonArray<JsonObject>)
        .filter {
            it["Name"] != ".ctor"
        }.filter {
            val unique = !names.contains(it["Name"].toString())
            names.add(it["Name"].toString())
            unique
        }
        .map { it to tsb }
        .map(::addMethodToType)

    val ctor = FunSpec.constructorBuilder()
        .addParameter(ParameterSpec.builder("ptr", Pointer::class.asTypeName().copy(true)).defaultValue("Pointer.NULL").build()).build()

    tsb.primaryConstructor(ctor)
    tsb.addSuperclassConstructorParameter("ptr")

    val fieldOrder = AnnotationSpec.builder(FieldOrder::class)
        .addMember(
            tsb.propertySpecs.map(PropertySpec::name).map { "\"$it\"" }.reduceRight { l, r -> "${l},${r}" }
        ).build()
    tsb.addAnnotation(fieldOrder)

    val guidAttr = selectGuidAttribute(obj["CustomAttributes"] as JsonArray<JsonObject>)
    val improperGUID = guidAttr?.let { getImproperGUID(it) }
    val properGUID = improperGUID?.let { properGUID(it) }


    val iidField = properGUID?.let {
        PropertySpec.builder("IID", Guid.IID::class, KModifier.FINAL).initializer(
            "%T(\"$it\")", Guid.IID::class
        ).build()
    }

    val co = TypeSpec.companionObjectBuilder()
    iidField?.let { co.addProperty(it) }
    co.addProperty(PropertySpec.builder("className", String::class).initializer("\"${obj["FullName"]}\"").build())
    co?.let { tsb.addType(it.build()) }

    fs.addType(tsb.build())


    return winrtInterface
}

fun isInterface(classJsonObject: JsonObject): Boolean {
    return classJsonObject["IsInterface"] as Boolean
}

fun selectGuidAttribute(customAttributes: JsonArray<JsonObject>): JsonObject? {
    return customAttributes.firstOrNull {
        (it["AttributeType"] as JsonObject)["Name"].toString() == "GuidAttribute"
    }
}

fun getImproperGUID(guidAttribute: JsonObject): List<Long> {
    return (guidAttribute["ConstructorArguments"] as JsonArray<JsonObject>)
        .map { it["Value"].toString().toLong() }
        .toList()
}

fun properGUID(bytes: List<Long>): String {
    return bytes.map { it.toString(16) }.map(String::toUpperCase).reduceRight(String::plus)
}

fun addMethodToType(methodBuilderPair: Pair<JsonObject, TypeSpec.Builder>): TypeSpec.Builder {
    val (method, ts) = methodBuilderPair
    val projection = getMethodInterface(method)

    ts.addProperty(
        PropertySpec.builder(
            method["Name"].toString().replaceFirstChar { it.lowercase(Locale.getDefault()) },
            ClassName("", listOf(method["Name"].toString().capitalize())).copy(true)
        ).mutable(true).addAnnotation(JvmField::class).initializer("null").build()
    )

    ts.addType(projection)
    return ts
}

fun getMethodInterface(method: JsonObject): TypeSpec {
    val name = method["Name"]
    val invoke = FunSpec.builder("invoke").addParameter("thisPtr", Pointer::class)
    (method["Parameters"] as JsonArray<JsonObject>).forEach {
        invoke.addParameter(it["Name"].toString(), getParameterTypeMapping(it))
    }

    val returnType = (method["ReturnType"] as JsonObject)["Name"]
    invoke.addParameter(ParameterSpec("returnValue", getTypeMappingByReference(returnType.toString())))
    invoke.addModifiers(KModifier.ABSTRACT)
    invoke.returns(WinNT.HRESULT::class)
    return TypeSpec.interfaceBuilder(
        name.toString()
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault())
                else it.toString()
            }
    ).addFunction(invoke.build()).addSuperinterface(Callback::class.asTypeName()).build()
}

fun getParameterTypeMapping(parameter: JsonObject): TypeName {
    var parameterTypeName = (parameter["ParameterType"] as JsonObject)["Name"]
    return getTypeMapping(parameterTypeName.toString())
}

private fun getTypeMapping(parameterTypeName: String?) = when (parameterTypeName) {
    "UInt32" -> Int::class.asTypeName()
    "String" -> WinNT.HANDLE::class.asTypeName()
    "Double" -> Double::class.asTypeName()
    "Boolean" -> Byte::class.asTypeName()
    else -> Pointer::class.asTypeName()
}

private fun getTypeMappingByReference(typeName: String?): TypeName {
    return when (typeName) {
        "UInt32" -> IntByReference::class.asTypeName()
        "String" -> WinNT.HANDLEByReference::class.asTypeName()
        "Double" -> DoubleByReference::class.asTypeName()
        "Boolean" -> ByteByReference::class.asTypeName()
        else -> PointerByReference::class.asTypeName()
    }
}