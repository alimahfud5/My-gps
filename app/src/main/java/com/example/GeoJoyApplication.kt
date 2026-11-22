package com.example

import android.app.Application
import com.example.data.GeoJoyDatabase
import com.example.data.LocationRepository
import com.example.data.SettingsRepository
import com.example.location.MockLocationController
import org.osmdroid.config.Configuration

class GeoJoyApplication : Application() {

    lateinit var database: GeoJoyDatabase
        private set

    lateinit var locationRepository: LocationRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var mockLocationController: MockLocationController
        private set

    override fun onCreate() {
        super.onCreate()
        // Configure osmdroid user agent & tile cache
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/1.0"
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir.resolve("osmdroid_tiles")
        }

        database = GeoJoyDatabase.getInstance(this)
        locationRepository = LocationRepository(database.locationDao())
        settingsRepository = SettingsRepository(this)
        mockLocationController = MockLocationController(this, settingsRepository)
    }
}
