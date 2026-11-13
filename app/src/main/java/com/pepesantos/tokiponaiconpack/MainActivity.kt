package com.pepesantos.tokiponaiconpack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pepesantos.tokiponaiconpack.ui.EditorScreen
import com.pepesantos.tokiponaiconpack.ui.theme.TokiPonaTheme

/**
 * Pantalla principal: editor de sitelen pona.
 * Los launchers además detectan el icon pack por los intent-filter de esta actividad
 * en el manifiesto y leen res/xml/appfilter.xml.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TokiPonaTheme {
                EditorScreen()
            }
        }
    }
}
