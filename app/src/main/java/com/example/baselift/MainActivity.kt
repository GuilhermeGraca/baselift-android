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

import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Ignorar se negado, a app funciona na mesma sem agendar
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pedir permissão no Android 13+ logo na primeira vez
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        enableEdgeToEdge()
        val appContainer = (application as BaseLiftApplication).container

        // Injetar dados de demonstração localmente (utiliza reflexão para evitar erros de compilação a quem clonar o repositório sem o ficheiro)
        //Comentar o que está dentro do if se nao quiser mock data
        if (savedInstanceState == null) {
            // try {
            //     val clazz = Class.forName("com.example.baselift.MockDataInjector")
            //     val instance = clazz.getDeclaredField("INSTANCE").get(null)
            //     clazz.methods.find { it.name == "inject" }?.invoke(instance, this, true)
            // } catch (_: Exception) {
            //     // Ignora silenciosamente se o ficheiro não existir (ex: repositório clonado)
            // }
        }

        setContent {
            BaseLiftTheme {
                AppNavigation(appContainer = appContainer, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
