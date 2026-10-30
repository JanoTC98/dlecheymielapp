package com.grupo4.dlecheymielapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grupo4.dlecheymielapp.data.auth.AuthRole
import com.grupo4.dlecheymielapp.data.auth.AuthUser
import com.grupo4.dlecheymielapp.ui.home.RoleHomeScreen
import com.grupo4.dlecheymielapp.ui.login.LoginScreen
import com.grupo4.dlecheymielapp.ui.login.LoginUiState
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme

@Composable
private fun Framed(content: @Composable () -> Unit) = DLecheTheme { Surface(Modifier.fillMaxSize()) { content() } }

@Preview(name = "Login", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun LoginPreview() = Framed {
    LoginScreen(LoginUiState(email = "ana@dlecheymiel.pe", password = "secreto"), {}, {}, {})
}

@Preview(name = "Login con error", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun LoginErrorPreview() = Framed {
    LoginScreen(LoginUiState(email = "ana@dlecheymiel.pe", password = "x", error = "Correo o contraseña incorrectos."), {}, {}, {})
}

@Preview(name = "Login cargando", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun LoginLoadingPreview() = Framed {
    LoginScreen(LoginUiState(email = "ana@dlecheymiel.pe", password = "x", loading = true), {}, {}, {})
}

@Preview(name = "Inicio Encargado", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun HomePreview() = Framed {
    RoleHomeScreen(
        AuthUser(id = 7, firstName = "Ana", lastName = "Ruiz", email = "ana@dlecheymiel.pe", role = AuthRole.DELIVERY_HANDLER),
        onLogout = {},
    )
}
