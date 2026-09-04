package com.tala.engine.model

enum class BarcodeFormat(val id: Int, val displayName: String) {
    QR_CODE(0, "QR Code"),
    CODE_128(1, "Code 128");

    companion object {
        fun fromId(id: Int): BarcodeFormat? = entries.find { it.id == id }
    }
}
