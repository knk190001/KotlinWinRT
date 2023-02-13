package com.github.knk190001.winrtbinding

import Windows.Data.Json.IJsonValue
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



    val values = (0..10).map {
        JsonValue.CreateNumberValue(it.toDouble())
    }
    values.forEach {
        jsonArray.Append(it.IJsonValue_Interface)
    }
    val items = arrayOf(IJsonValue())
    jsonArray.GetMany(UINT(0), items)
    println(items[0].Stringify())
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
