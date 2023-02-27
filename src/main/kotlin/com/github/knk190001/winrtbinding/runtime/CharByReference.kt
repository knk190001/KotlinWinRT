package com.github.knk190001.winrtbinding.runtime

import com.sun.jna.IntegerType
import com.sun.jna.platform.win32.COM.Unknown.ByReference

class CharByReference : ByReference() {
    fun getValue(): Char {
        return pointer.getChar(0)
    }
}