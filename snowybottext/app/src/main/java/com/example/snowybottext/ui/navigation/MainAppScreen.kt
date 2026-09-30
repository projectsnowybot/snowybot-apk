package com.example.snowybottext.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.snowybottext.ui.dashboard.DashboardScreen
import com.example.snowybottext.ui.theme.SnowybottextTheme

@Composable
fun MainAppScreen(
    modifier: Modifier = Modifier,
    onImportSnowybot: ((String) -> Unit) -> Unit = {},
    readSnowybotSource: () -> String? = { null },
    webSession: Any? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            DashboardScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 800)
@Composable
fun MainAppScreenPreview() {
    SnowybottextTheme {
        MainAppScreen()
    }
}
