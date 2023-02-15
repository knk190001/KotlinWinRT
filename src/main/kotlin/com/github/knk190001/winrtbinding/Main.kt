package com.github.knk190001.winrtbinding

import Windows.Data.Json.IJsonValue
import Windows.Data.Json.JsonArray
import Windows.Data.Json.JsonObject
import Windows.Data.Json.JsonValue
import Windows.Data.Text.SelectableWordSegmentsTokenizingHandler
import Windows.Data.Text.SelectableWordsSegmenter
import Windows.Data.Xml.Dom.XmlDocument
import Windows.UI.Notifications.ToastNotification
import Windows.UI.Notifications.ToastNotificationManager
import Windows.UI.Notifications.ToastNotificationManagerForUser
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinNT.*


fun main(args: Array<String>) {
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
    jsonObject.SetNamedValue("nullProperty", jsonValue2.IJsonValue_Interface)
    println(jsonObject.Stringify())

    println(jsonValue.get_ValueType())
    println(jsonValue2.get_ValueType())

    val selectableWordsSegmenter = SelectableWordsSegmenter("en-US")
    println(selectableWordsSegmenter.get_ResolvedLanguage())

    val tokenizingHandler = SelectableWordSegmentsTokenizingHandler.create { precedingWords, words ->
        val preItr = precedingWords.First()
        while (preItr.get_HasCurrent()) {
            println("Preceding: ${preItr.get_Current().get_Text()}")
            preItr.MoveNext()
        }

        val itr = words.First()
        while (itr.get_HasCurrent()) {
            println("Words: ${itr.get_Current().get_Text()}")
            itr.MoveNext()
        }
    }

    selectableWordsSegmenter.Tokenize("Hello World!", UINT(0), tokenizingHandler)

    val xmlToastTemplate = "<toast launch=\"app-defined-string\">" +
            "<visual>" +
            "<binding template =\"ToastGeneric\">" +
            "<text>Sample Notification</text>" +
            "<text>" +
            "This is a sample toast notification from kunal-chowdhury.com" +
            "</text>" +
            "</binding>" +
            "</visual>" +
            "</toast>"

    val xmlDoc = XmlDocument()
    xmlDoc.LoadXml(xmlToastTemplate)

    val toastNotification = ToastNotification(xmlDoc)
    val toastNotificationManager = ToastNotificationManager.CreateToastNotifier("032467F0-6AF8-47AB-8689-918E51874DBF_92eg4pjhhyp4c!App")
    toastNotificationManager.Show(toastNotification)
}

fun HRESULT.print(functionName: String) {
    println("$functionName: ${Integer.toHexString(this.toInt())}")
}
