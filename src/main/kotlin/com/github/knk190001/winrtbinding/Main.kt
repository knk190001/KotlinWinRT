package com.github.knk190001.winrtbinding

import Windows.Data.Json.JsonArray
import Windows.Data.Json.JsonObject
import Windows.Data.Json.JsonValue
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary


interface JNAApiInterface : StdCallLibrary {
    fun RoActivateInstance(filter: HANDLE, pref: PointerByReference): HRESULT
    fun RoGetActivationFactory(activatableClassId: HANDLE, iid: REFIID, factory: PointerByReference): HRESULT
    fun RoInitialize(initType: Int): HRESULT
    fun RoGetParameterizedTypeInstanceIID(nameElementCount: UINT, nameElements: Pointer, metadataLocator: Pointer?, iid: Guid.GUID.ByReference, pExtra: Pointer?): HRESULT
    fun WindowsCreateString(sourceString: WString, length: Int, string: HANDLEByReference): HRESULT
    fun WindowsGetStringRawBuffer(str: HANDLE, length: IntByReference): WString

    companion object {
        val INSTANCE = Native.load(
            "combase",
            JNAApiInterface::class.java
        ) as JNAApiInterface
    }
}

fun main() {
    testV2()

}

fun testV2() {
    JNAApiInterface.INSTANCE.RoInitialize(1)
    val jsonObject = JsonObject()
    val jsonArray = JsonArray()
    val jsonValue = JsonValue.CreateStringValue("Hello world")
    val jsonValue2 = JsonValue.CreateNullValue()
    println(jsonObject.Stringify())
    println(jsonArray.Stringify())

    jsonArray.Append(jsonValue.IJsonValue_Interface)
    println(jsonArray.Stringify())

    jsonObject.SetNamedValue("array", jsonArray.IJsonValue_Interface)
    jsonObject.SetNamedValue("nullProperty",jsonValue2.IJsonValue_Interface)
    println(jsonObject.Stringify())

    println(jsonValue.get_ValueType())
    println(jsonValue2.get_ValueType())
}

fun HRESULT.print(functionName: String) {
    println("$functionName: ${Integer.toHexString(this.toInt())}")
}
fun checkHR(hr: HRESULT) {
    if (hr.toInt() != 0) {
        throw Win32Exception(hr)
    }
}
fun String.toHandle():HANDLE {
    val wString = WString(this)
    val handleByReference = HANDLEByReference()
    val hr = JNAApiInterface.INSTANCE.WindowsCreateString(wString, this.length, handleByReference)
    return handleByReference.value
}

fun HANDLE.handleToString():String {
    val ibr = IntByReference()
    val wstr = JNAApiInterface.INSTANCE.WindowsGetStringRawBuffer(this,ibr)
    return wstr.toString()
}