package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.HilalCheckerApp
import com.example.ui.HilalViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HilalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HilalCheckerApp(viewModel = viewModel)
        }
    }
}
