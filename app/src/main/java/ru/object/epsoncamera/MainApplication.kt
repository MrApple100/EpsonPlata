package ru.`object`.epsoncamera

import android.app.Application
import androidx.lifecycle.viewmodel.viewModelFactory
import org.koin.android.viewmodel.dsl.viewModel
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import ru.`object`.epsoncamera.epsonRTSP.live.ReceiveViewModel

class MainApplication : Application() {

    val viewModelModule = module {
        single {
            ReceiveViewModel()
        }
    }
    override fun onCreate() {
        super.onCreate()

        startKoin {

            modules(viewModelModule)
        }
        loadKoinModules(viewModelModule)

    }

    override fun onTerminate() {
        super.onTerminate()
        unloadKoinModules(viewModelModule)
    }
}