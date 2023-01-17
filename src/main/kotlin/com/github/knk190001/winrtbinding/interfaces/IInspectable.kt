package com.github.knk190001.winrtbinding.interfaces

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.WinNT.HANDLEByReference
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

@FieldOrder("iUnknown", "getIids", "getRuntimeClassName", "getTrustLevel")
class IInspectable(ptr: Pointer? = Pointer.NULL) : Structure(ptr) {
    @JvmField
    var iUnknownVtbl: IUnknownVtbl? = null
    @JvmField
    var getIids: GetIids? = null
    @JvmField
    var getRuntimeClassName: GetRuntimeClassName? = null
    @JvmField
    var getTrustLevel: GetTrustLevel? = null

    interface GetIids : Callback {
        fun invoke(thisPointer: Pointer, iidCount: IntByReference, pointer: PointerByReference): HRESULT
    }

    interface GetRuntimeClassName : Callback {
        fun invoke(thisPointer: Pointer, className: HANDLEByReference): HRESULT
    }

    interface GetTrustLevel : Callback {
        fun invoke(thisPointer: Pointer, trustLevel: IntByReference): HRESULT
    }
}