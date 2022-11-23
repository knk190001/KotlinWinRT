import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference
@FieldOrder("queryInterface","addRef","release")
class IUnknown(ptr: Pointer? = Pointer.NULL) : Structure(ptr) {
    @JvmField
    var queryInterface: QueryInterface? = null

    @JvmField
    var addRef: AddRef? = null

    @JvmField
    var release: Release? = null

    interface QueryInterface : Callback {
        fun invoke(thisPtr: Pointer, iid: Guid.REFIID, returnValue: PointerByReference): HRESULT
    }

    interface AddRef : Callback {
        fun invoke(thisPtr: Pointer): HRESULT
    }

    interface Release : Callback {
        fun invoke(thisPtr: Pointer): HRESULT
    }
}