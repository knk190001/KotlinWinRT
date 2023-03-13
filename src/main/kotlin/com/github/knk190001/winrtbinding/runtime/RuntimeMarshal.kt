package com.github.knk190001.winrtbinding.runtime

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT.HANDLE
import kotlin.reflect.KClass


interface Marshal<T : Any, R : Any> {
    val fromType: KClass<T>
    val toType: KClass<R>

    val nativeNullValue: R?
    val managedNullValue: T?

    fun toNative(t: T): R

    fun fromNative(t: R): T

}

val marshals: List<Marshal<*, *>> = listOf(BooleanMarshal(), StringMarshal())

inline fun <reified T : Any> marshalToNative(t: T?): Any? {
    @Suppress("UNCHECKED_CAST")
    val marshal: Marshal<T, *> = marshals.singleOrNull {
        it.fromType == T::class
    } as Marshal<T, *>? ?: return t

    if (t == null) {
        return marshal.nativeNullValue
    }

    return marshal.toNative(t)
}

inline fun <reified T : Any, reified R : Any> marshalFromNative(t: R?): T? {
    if (T::class == R::class) {
        return t as T
    }
    @Suppress("UNCHECKED_CAST")
    val marshal: Marshal<T, R> = marshals.singleOrNull {
        it.fromType == T::class && it.toType == R::class
    } as Marshal<T, R>? ?: return t as T

    if (t == null) {
        return marshal.managedNullValue
    }
    return marshal.fromNative(t)
}

class BooleanMarshal : Marshal<Boolean, Byte> {
    override val fromType = Boolean::class
    override val toType = Byte::class

    override val managedNullValue = false
    override val nativeNullValue: Byte = 0

    override fun fromNative(t: Byte): Boolean {
        return t.toInt() != 0
    }

    override fun toNative(t: Boolean): Byte {
        return if (t) 1 else 0
    }
}

class StringMarshal : Marshal<String, HANDLE> {
    override val fromType = String::class
    override val toType = HANDLE::class

    override val managedNullValue = ""
    override val nativeNullValue = HANDLE(Pointer.NULL)

    override fun fromNative(t: HANDLE): String {
        return t.handleToString()
    }

    override fun toNative(t: String): HANDLE {
        return t.toHandle()
    }

}

