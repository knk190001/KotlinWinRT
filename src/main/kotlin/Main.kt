import Windows.Data.Json.JsonArray
import Windows.Data.Json.JsonObject
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.platform.win32.Win32Exception
import com.sun.jna.platform.win32.WinNT.*
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference


interface JNAApiInterface : Library {
    fun RoActivateInstance(filter: HANDLE, pref: PointerByReference): HRESULT
    fun RoGetActivationFactory(activatableClassId: HANDLE, iid: REFIID, factory: PointerByReference): HRESULT
    fun RoInitialize(initType: Int): HRESULT
    fun WindowsCreateString(sourceString: WString, length: Int, string: HANDLEByReference): HRESULT
    fun WindowsGetStringRawBuffer(str: HANDLE, length: IntByReference): WString

    companion object {
        val INSTANCE = Native.loadLibrary(
            "combase",
            JNAApiInterface::class.java
        ) as JNAApiInterface
    }
}

fun main() {
//    var hr: HRESULT
//    hr = JNAApiInterface.INSTANCE.RoInitialize(1)
//    hr.print("RoInitialize")
//
//    val refiid = REFIID(IJsonObjectStatics.IID)
//
//    val activationFactoryClassHandle = "Windows.Data.Json.JsonObject".toHandle()
//
//    val factoryRef = PointerByReference()
//    hr = JNAApiInterface.INSTANCE.RoGetActivationFactory(activationFactoryClassHandle, refiid, factoryRef)
//    hr.print("RoGetActivationFactory")
//
//    val iJsonObjectStatics = IJsonObjectStatics(factoryRef.value.getPointer(0))
//    iJsonObjectStatics.read()
//    iJsonObjectStatics.autoRead = true
//
//    val jsonHandle = "{}".toHandle()
//
//    val jsonObjPoiner = PointerByReference()
//    hr = iJsonObjectStatics.parse!!.invoke(iJsonObjectStatics.pointer, jsonHandle, jsonObjPoiner)
//    hr.print("IJsonObjectStatics::parse")
//
//    val iJsonObjectIUnknown = IUnknown(jsonObjPoiner.value.getPointer(0))
//    iJsonObjectIUnknown.read()
//
//    val refiid2 = Guid.REFIID(IStringable.IID)
//    val iStringablePtr = PointerByReference()
//    hr = iJsonObjectIUnknown.queryInterface!!.invoke(
//        jsonObjPoiner.value,
//        refiid2,
//        iStringablePtr
//    )
//    hr.print("IUnknown::queryInterface")
//
//    val iStringable = IStringable(iStringablePtr.value.getPointer(0))
//    iStringable.read()
//
//    val toStringHandle: HANDLEByReference = HANDLEByReference()
//    hr = iStringable.toString!!.invoke(iStringablePtr.value, toStringHandle)
//    hr.print("IStringable::toString")
//
//    println(toStringHandle.value.handleToString())
//
//    val runtimeClassName = HANDLEByReference()
//    iStringable.iInspectable!!.getRuntimeClassName!!.invoke(iStringablePtr.value,runtimeClassName)
//    println(runtimeClassName.value.handleToString())
    testV2()

}

fun testV2() {
    JNAApiInterface.INSTANCE.RoInitialize(1)
    val jsonObject = JsonObject()

    println(jsonObject.Stringify())
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
    hr.print("WindowsCreateString")
    return handleByReference.value
}

fun HANDLE.handleToString():String {
    val ibr = IntByReference()
    val wstr = JNAApiInterface.INSTANCE.WindowsGetStringRawBuffer(this,ibr)
    return wstr.toString()
}

fun test() {

}