import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid.REFIID
import com.sun.jna.ptr.PointerByReference

class JsonValue(ptr: Pointer = Pointer.NULL) : PointerType(ptr) {

    fun asIStringable(): IStringable {
        val stringableIID = REFIID(IStringable.IID)
        val ref = PointerByReference()
        IUnknown(pointer.getPointer(0)).queryInterface(pointer, stringableIID, ref)
        return IStringable(ref.value)
    }
}