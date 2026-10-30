package com.grupo4.dlecheymielapp

import android.app.Application

class DLecheApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
