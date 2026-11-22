package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.PdfPaperSize
import com.example.model.SortOption
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "folioflow_prefs")

data class AppSettings(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val pdfPaperSize: PdfPaperSize = PdfPaperSize.A4,
    val autoStripExifOnConvert: Boolean = true,
    val editorFontSizeSp: Int = 15,
    val sortOption: SortOption = SortOption.DATE_DESC
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PDF_PAPER_SIZE = stringPreferencesKey("pdf_paper_size")
        val AUTO_STRIP_EXIF = booleanPreferencesKey("auto_strip_exif")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val SORT_OPTION = stringPreferencesKey("sort_option")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
            pdfPaperSize = prefs[Keys.PDF_PAPER_SIZE]?.let { runCatching { PdfPaperSize.valueOf(it) }.getOrNull() }
                ?: PdfPaperSize.A4,
            autoStripExifOnConvert = prefs[Keys.AUTO_STRIP_EXIF] ?: true,
            editorFontSizeSp = prefs[Keys.EDITOR_FONT_SIZE] ?: 15,
            sortOption = prefs[Keys.SORT_OPTION]?.let { runCatching { SortOption.valueOf(it) }.getOrNull() }
                ?: SortOption.DATE_DESC
        )
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setPdfPaperSize(size: PdfPaperSize) {
        context.dataStore.edit { it[Keys.PDF_PAPER_SIZE] = size.name }
    }

    suspend fun setAutoStripExif(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_STRIP_EXIF] = enabled }
    }

    suspend fun setEditorFontSize(sizeSp: Int) {
        context.dataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(12, 24) }
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }
}
