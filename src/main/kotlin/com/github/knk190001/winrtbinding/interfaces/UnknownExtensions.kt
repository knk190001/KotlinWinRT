package com.github.knk190001.winrtbinding.interfaces

import com.sun.jna.platform.win32.COM.Unknown

fun Unknown.ByReference.getValue(): Unknown {
    return this
}