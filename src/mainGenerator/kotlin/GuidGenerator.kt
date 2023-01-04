import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Guid.GUID
import memeid.UUID
import model.SparseEntity
import model.SparseInterface
import model.SparseTypeReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.math.sin


object GuidGenerator {
    fun getSignature(typeReference: SparseTypeReference, lookup: (SparseTypeReference) -> SparseEntity): String {
        if (typeReference.isTypeOf("System", "Object")) {
            return "cinterface(IInspectable)"
        }
        if (typeReference.isTypeOf("System", "String")) {
            return "string"
        }
        //TODO: GetGuidSignature
        //TODO: IsValueType
        //TODO: Struct Entity
        //TODO: Enum Entity

        if (typeReference.genericParameters != null) {
            val typeParameters = typeReference.genericParameters.map { it.type }
                .map { getSignature(it!!, lookup) }
                .joinToString(";")

            val entity = lookup(typeReference)
            return when (entity) {
                is SparseInterface -> "pinterface(${entity.guid.guidToSignatureFormat()};$typeParameters)"
                else -> throw IllegalArgumentException("Non interface type reference")
            }
        }
        val sInterface = lookup(typeReference) as SparseInterface
        return sInterface.guid.guidToSignatureFormat()
    }

    private val wrt_pinterface_namespace_native = GUID("11f47ad5-7b73-42c0-abae-878b1e16adee")
    private val wrt_pinterface_namespace_java = UUID.fromString("11f47ad5-7b73-42c0-abae-878b1e16adee")
    fun CreateIID(type: SparseTypeReference, lookup: (SparseTypeReference) -> SparseEntity): Guid.GUID? {
        val signature: String = getSignature(type, lookup)
        val maxByteCount = (StandardCharsets.UTF_8.newEncoder().maxBytesPerChar() * signature.length).toInt()
        var array: ByteArray = ByteArray(16 + maxByteCount)
        val wrtPinterfaceNamespace: ByteArray = wrt_pinterface_namespace_native.toByteArray()
        System.arraycopy(wrtPinterfaceNamespace, 0, array, 0, wrtPinterfaceNamespace.size)
        val signatureBytes: ByteArray = signature.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(signatureBytes, 0, array, 16, signatureBytes.size)
        array = array.copyOfRange(0, 16 + signatureBytes.size)
        return UUID.V5.from(wrt_pinterface_namespace_java, signature).toString()
            .let { GUID.fromString(it) }
    }

    private fun SparseTypeReference.isTypeOf(namespace: String, name: String): Boolean {
        return this.namespace == namespace && this.name == name
    }

    private fun String.guidToSignatureFormat(): String {
        return GUID.fromString(this).toGuidString().lowercase()
    }
}
