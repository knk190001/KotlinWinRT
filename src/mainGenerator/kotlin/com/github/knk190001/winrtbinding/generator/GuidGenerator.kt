package com.github.knk190001.winrtbinding.generator

import com.github.knk190001.winrtbinding.generator.model.entities.IProjectable
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Guid.GUID
import memeid.UUID
import com.github.knk190001.winrtbinding.generator.model.entities.SparseInterface
import com.github.knk190001.winrtbinding.generator.model.entities.SparseTypeReference
import java.nio.charset.StandardCharsets


object GuidGenerator {
    fun getSignature(typeReference: SparseTypeReference, lookup: LookUpFn): String {
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
        val sInterface = lookup(typeReference)
        return sInterface.guid.guidToSignatureFormat()
    }

    private val wrtPInterfaceNamespaceNative = GUID("11f47ad5-7b73-42c0-abae-878b1e16adee")
    private val wrtPinterfaceNamespaceJava = UUID.fromString("11f47ad5-7b73-42c0-abae-878b1e16adee")
    fun CreateIID(type: SparseTypeReference, lookup: LookUpFn): Guid.GUID? {
        val signature: String = getSignature(type, lookup)
        val maxByteCount = (StandardCharsets.UTF_8.newEncoder().maxBytesPerChar() * signature.length).toInt()
        var array: ByteArray = ByteArray(16 + maxByteCount)
        val wrtPinterfaceNamespace: ByteArray = wrtPInterfaceNamespaceNative.toByteArray()
        System.arraycopy(wrtPinterfaceNamespace, 0, array, 0, wrtPinterfaceNamespace.size)
        val signatureBytes: ByteArray = signature.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(signatureBytes, 0, array, 16, signatureBytes.size)
        array = array.copyOfRange(0, 16 + signatureBytes.size)
        return UUID.V5.from(wrtPinterfaceNamespaceJava, signature).toString()
            .let { GUID.fromString(it) }
    }

    private fun SparseTypeReference.isTypeOf(namespace: String, name: String): Boolean {
        return this.namespace == namespace && this.name == name
    }

    private fun String.guidToSignatureFormat(): String {
        return GUID.fromString(this).toGuidString().lowercase()
    }
}
