package com.example.arsens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.arsens.ui.WorkflowApp
import com.example.arsens.ui.theme.ARSensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ARSensTheme {
                WorkflowApp()
            }
        }
    }
}
