package com.econverter.app

import androidx.annotation.StringRes

enum class ConversionBackend(
    val label: String,
    @StringRes val labelRes: Int = 0,
    @StringRes val descRes: Int = 0,
) {
    AUTO("Auto", R.string.backend_auto_label, R.string.backend_auto_desc),
    CALIBRE("Calibre", R.string.backend_calibre_label, R.string.backend_calibre_desc),
    BOKO("boko", R.string.backend_boko_label, R.string.backend_boko_desc),
    ;

    val inputFormats: Set<String>
        get() = when (this) {
            AUTO -> CALIBRE.inputFormats + BOKO.inputFormats
            CALIBRE -> setOf("epub", "mobi", "azw3", "docx", "odt", "fb2", "html", "htmlz", "lrf", "pdb", "rtf", "txt", "md", "markdown", "pdf")
            BOKO -> setOf("epub", "azw3", "mobi", "kfx")
        }

    val outputFormats: List<String>
        get() = when (this) {
            AUTO -> listOf("epub", "kfx", "mobi", "azw3", "docx", "fb2", "htmlz", "lrf", "md", "txt", "txtz")
            CALIBRE -> listOf("epub", "mobi", "azw3", "docx", "fb2", "htmlz", "lrf", "txt", "txtz")
            BOKO -> listOf("epub", "kfx", "azw3", "md")
        }
}
