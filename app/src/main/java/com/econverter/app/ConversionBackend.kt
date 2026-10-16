package com.econverter.app

enum class ConversionBackend(val label: String, val description: String) {
    AUTO("Auto", "Combines Calibre and boko for the widest format support. Conversion options are applied through Calibre."),
    CALIBRE("Calibre", "Broad format support with metadata, font, layout, and text options. No KFX input or output."),
    BOKO("boko", "Direct native conversion for EPUB, KFX, AZW3, and MOBI books. Calibre conversion options are unavailable."),
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
