

package com.example.d_r_i_v_e

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.d_r_i_v_e.frontend.AppNavigation
import com.example.d_r_i_v_e.frontend.theme.D_R_I_V_ETheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            D_R_I_V_ETheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Screens pad for system bars themselves; consuming the bottom inset here stops it being applied twice.
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(WindowInsets.navigationBars)
                    ) {
                        AppNavigation()
                    }
                }
            }
        }
    }
}
