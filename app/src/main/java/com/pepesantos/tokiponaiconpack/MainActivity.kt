package com.pepesantos.tokiponaiconpack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pepesantos.tokiponaiconpack.ui.EditorScreen
import com.pepesantos.tokiponaiconpack.ui.IconGalleryScreen
import com.pepesantos.tokiponaiconpack.ui.theme.TokiPonaTheme

/**
 * Pantalla principal: editor de sitelen pona, con acceso a la galería de iconos del pack.
 * Los launchers además detectan el icon pack por los intent-filter de esta actividad
 * en el manifiesto y leen res/xml/appfilter.xml.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TokiPonaTheme {
                var showGallery by rememberSaveable { mutableStateOf(false) }
                if (showGallery) {
                    BackHandler { showGallery = false }
                    IconGalleryScreen(onClose = { showGallery = false })
                } else {
                    EditorScreen(onOpenGallery = { showGallery = true })
                }
            }
        }
    }
}
