package com.github.knk190001.winrtbinding

import com.sun.jna.*
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import kotlin.Any
import kotlin.Int
import kotlin.String
import com.sun.jna.Function as JnaFunction

val WinRT = JNAApiInterface.INSTANCE

interface JNAApiInterface : StdCallLibrary {
    fun RoActivateInstance(filter: WinNT.HANDLE, pref: PointerByReference): WinNT.HRESULT
    fun RoGetActivationFactory(activatableClassId: WinNT.HANDLE, iid: Guid.REFIID, factory: PointerByReference): WinNT.HRESULT
    fun RoInitialize(initType: Int): WinNT.HRESULT
    fun RoGetParameterizedTypeInstanceIID(nameElementCount: WinDef.UINT, nameElements: Pointer, metadataLocator: Pointer?, iid: Guid.GUID.ByReference, pExtra: Pointer?): WinNT.HRESULT
    fun WindowsCreateString(sourceString: WString, length: Int, string: WinNT.HANDLEByReference): WinNT.HRESULT
    fun WindowsDeleteString(hstring: HANDLE?): Int

    fun WindowsGetStringRawBuffer(str: WinNT.HANDLE, length: IntByReference): WString

    companion object {
        val INSTANCE = Native.load(
            "combase",
            JNAApiInterface::class.java
        ) as JNAApiInterface
    }
}

fun checkHR(hr: WinNT.HRESULT) {
    if (hr.toInt() != 0) {
        throw Win32Exception(hr)
    }
}
fun String.toHandle(): WinNT.HANDLE {
    val wString = WString(this)
    val handleByReference = WinNT.HANDLEByReference()
    val hr = JNAApiInterface.INSTANCE.WindowsCreateString(wString, this.length, handleByReference)
    return handleByReference.value
}

fun WinNT.HANDLE.handleToString():String {
    val ibr = IntByReference()
    val wstr = JNAApiInterface.INSTANCE.WindowsGetStringRawBuffer(this,ibr)
    return wstr.toString()
}

private val typeMapper = WinRTTypeMapper()

class WinRTTypeMapper : DefaultTypeMapper() {
    init {
        val booleanConverter: TypeConverter = object : TypeConverter {
            override fun toNative(value: Any, context: ToNativeContext): Any {
                return if (value as Boolean) {
                    1
                }else {
                    0
                }.toByte()
            }

            override fun fromNative(value: Any, context: FromNativeContext): Boolean {
                return (value as Byte).toInt() != 0
            }

            override fun nativeType(): Class<*>? {
                return Byte::class.javaPrimitiveType
            }

        }

        val stringConverter: TypeConverter = object : TypeConverter {
            override fun toNative(value: Any, context: ToNativeContext): HANDLE {
                val str = value as String
                return str.toHandle()
            }

            override fun fromNative(value: Any, context: FromNativeContext): String {
                val handle = value as HANDLE
                return handle.handleToString()
            }

            override fun nativeType(): Class<*>? {
                return Pointer::class.java
            }

        }

        addTypeConverter(Boolean::class.javaPrimitiveType,booleanConverter)
        addTypeConverter(String::class.java,stringConverter)

    }
}

private val winRTOptions = mapOf<String,Any?>(
    Library.OPTION_TYPE_MAPPER to typeMapper
)
fun JnaFunction.invokeHR(params: Array<Any?>):HRESULT {
    return this.invoke(HRESULT::class.java,params, winRTOptions) as HRESULT
}