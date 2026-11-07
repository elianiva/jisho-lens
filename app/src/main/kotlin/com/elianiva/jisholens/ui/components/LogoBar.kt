package com.elianiva.jisholens.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.elianiva.jisholens.R
import com.elianiva.jisholens.ui.theme.LocalDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogoBar() {
    val dark = LocalDarkTheme.current
    TopAppBar(
        title = {
            Image(
                painter = painterResource(if (dark) R.drawable.logo_light else R.drawable.logo_dark),
                contentDescription = "Jisho Lens",
                modifier = Modifier.height(28.dp),
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
