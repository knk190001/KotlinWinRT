import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinDef.UINTByReference
import com.sun.jna.platform.win32.WinNT.HANDLE
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

@FieldOrder("iInspectable", "toString")
class IStringable(ptr: Pointer? = Pointer.NULL) : Structure(ptr) {
    @JvmField
    var iInspectable: IInspectable? = null

    @JvmField
    var toString: ToString? = null

    interface ToString : Callback {
        fun invoke(thisPtr: Pointer, returnVal: HANDLEByReference): HRESULT
    }

    companion object {
        var IID = Guid.IID("96369F548EB648F0ABCEC1B211E627C3")
    }
}