package com.econverter.app

import androidx.annotation.StringRes
import com.econverter.app.FileExtensions.AZW3
import com.econverter.app.FileExtensions.DOCX
import com.econverter.app.FileExtensions.EPUB
import com.econverter.app.FileExtensions.FB2
import com.econverter.app.FileExtensions.HTML
import com.econverter.app.FileExtensions.HTMLZ
import com.econverter.app.FileExtensions.KFX
import com.econverter.app.FileExtensions.LRF
import com.econverter.app.FileExtensions.MARKDOWN
import com.econverter.app.FileExtensions.MD
import com.econverter.app.FileExtensions.MOBI
import com.econverter.app.FileExtensions.ODT
import com.econverter.app.FileExtensions.PDB
import com.econverter.app.FileExtensions.PDF
import com.econverter.app.FileExtensions.RTF
import com.econverter.app.FileExtensions.TXT
import com.econverter.app.FileExtensions.TXTZ

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
            CALIBRE -> setOf(EPUB, MOBI, AZW3, DOCX, ODT, FB2, HTML, HTMLZ, LRF, PDB, RTF, TXT, MD, MARKDOWN, PDF)
            BOKO -> setOf(EPUB, AZW3, MOBI, KFX)
        }

    val outputFormats: List<String>
        get() = when (this) {
            AUTO -> (CALIBRE.outputFormats + BOKO.outputFormats).distinct()
            CALIBRE -> listOf(EPUB, MOBI, AZW3, DOCX, FB2, HTMLZ, LRF, TXT, TXTZ)
            BOKO -> listOf(EPUB, KFX, AZW3, MD)
        }
}
