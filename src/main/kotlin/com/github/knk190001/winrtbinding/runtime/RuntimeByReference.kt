package com.github.knk190001.winrtbinding.runtime

import com.github.knk190001.winrtbinding.runtime.interfaces.IUnknown
import com.sun.jna.NativeMapped
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.ptr.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createInstance
import kotlin.reflect.full.functions
import kotlin.reflect.typeOf


interface IByReference<T> {
    fun getPointer(): Pointer
    fun getValue(): T
}

interface ISpecializable {
    fun specialize(type: KType)
}

inline fun <reified T> makeByReferenceType(): IByReference<T> {
    return makeByReferenceType(typeOf<T>())
}

@Suppress("UNCHECKED_CAST")
fun <T> makeByReferenceType(type: KType): IByReference<T> {
    val brtAnnotation = type.annotationOfType<WinRTByReference>()
    if (brtAnnotation == null) {
        return when (type.classifier) {
            USHORT::class -> UShortByReference() as IByReference<T>
            UINT::class -> UIntByReference() as IByReference<T>
            ULONG::class -> ULongByReference() as IByReference<T>
            Float::class -> FloatByReference() as IByReference<T>
            Double::class -> DoubleByReference() as IByReference<T>
            Boolean::class -> BooleanByReference() as IByReference<T>
            Short::class -> ShortByReference() as IByReference<T>
            Int::class -> IntByReference() as IByReference<T>
            Long::class -> LongByReference() as IByReference<T>
            Unit::class -> throw IllegalArgumentException("ByReference<Unit> can't exist")
            String::class -> StringByReference() as IByReference<T>
            IUnknown::class -> IUnknownByReference() as IByReference<T>
            Byte::class -> ByteByReference() as IByReference<T>
            Guid.GUID::class -> GuidByReference() as IByReference<T>
            Char::class -> CharByReference() as IByReference<T>
            else -> throw NotImplementedError("Type: ${type.classifier} is not handled")
        }
    }
    val iByReference = brtAnnotation.brClass.createInstance() as IByReference<T>
    if (iByReference is ISpecializable) {
        iByReference.specialize(type)
    }
    return iByReference
}

class UShortByReference : IByReference<USHORT>, USHORTByReference()
class UIntByReference : IByReference<UINT>, UINTByReference()
class ULongByReference : IByReference<ULONG>, ULONGByReference()
class FloatByReference : IByReference<Float>, com.sun.jna.ptr.FloatByReference()
class DoubleByReference : IByReference<Double>, com.sun.jna.ptr.DoubleByReference()
class BooleanByReference : IByReference<Byte>, com.sun.jna.ptr.ByteByReference()
class ShortByReference : IByReference<Short>, com.sun.jna.ptr.ShortByReference()
class IntByReference : IByReference<Int>, com.sun.jna.ptr.IntByReference()
class LongByReference : IByReference<Long>, com.sun.jna.ptr.LongByReference()
class StringByReference : IByReference<HANDLE>, HANDLEByReference()
class IUnknownByReference : IByReference<IUnknown>, IUnknown.ByReference()
class ByteByReference : IByReference<Byte>, com.sun.jna.ptr.ByteByReference()
class GuidByReference : IByReference<GUID>, GUID.ByReference() {
    override fun getValue(): GUID {
        return this
    }
}

class CharByReference : Unknown.ByReference(), IByReference<Char> {
    override fun getValue(): Char {
        return pointer.getChar(0)
    }
}