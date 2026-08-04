package com.example.baselift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.baselift.View.theme.BaseLiftTheme
import com.example.baselift.View.navigation.AppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as BaseLiftApplication).container
        
        // Injetar dados de demonstração localmente (utiliza reflexão para evitar erros de compilação a quem clonar o repositório sem o ficheiro)
        if (savedInstanceState == null) {
            try {
                val clazz = Class.forName("com.example.baselift.MockDataInjector")
                val instance = clazz.getDeclaredField("INSTANCE").get(null)
                clazz.methods.find { it.name == "inject" }?.invoke(instance, this, true)
            } catch (_: Exception) {
                // Ignora silenciosamente se o ficheiro não existir (ex: repositório clonado)
            }
        }
        
        setContent {
            BaseLiftTheme {
                AppNavigation(appContainer = appContainer, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
