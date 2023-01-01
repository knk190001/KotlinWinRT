import ABI.Windows.Data.Json.IJsonObject
import Windows.Data.Json.JsonObject
import com.sun.jna.FromNativeContext
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

class IJsonValue private constructor(ptr: Pointer) : PointerType(ptr) {
    val guid = IID("...")
    private val vtblPtr: Pointer
        get() = pointer.getPointer(0)

    object ABI

    fun getObjectAt(index: UINT): JsonObject {
        val fnPtr = vtblPtr.getPointer(6L * Native.POINTER_SIZE)
        val fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION)
        val result = JsonObject.ByReference()
        val hr = HRESULT(
            fn.invokeInt(arrayOf(pointer, index, result))
        )
        if (hr.toInt() != 0) {
            throw RuntimeException(hr.toString())
        }
        return result.getValue()
    }

    fun marshalString(str: String) {
        var toHandle = str.toHandle()
        toHandle.handleToString()
    }
}