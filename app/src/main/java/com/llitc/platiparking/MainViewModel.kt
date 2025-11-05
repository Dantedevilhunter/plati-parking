package com.llitc.platiparking

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dataManager = DataManager(application)

    // This is a new event channel to trigger payments from the Assistant
    private val _paymentTrigger = MutableSharedFlow<ParkingConfig>()
    val paymentTrigger = _paymentTrigger.asSharedFlow()

    val registrationNumber = dataManager.registrationNumberFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    val parkingConfigs = dataManager.parkingConfigsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun saveRegistration(number: String) {
        viewModelScope.launch {
            dataManager.saveRegistration(number)
        }
    }

    fun addParkingConfig(config: ParkingConfig) {
        viewModelScope.launch {
            val currentList = parkingConfigs.value.toMutableList()
            currentList.add(config)
            dataManager.saveParkingConfigs(currentList)
        }
    }

    fun removeParkingConfig(config: ParkingConfig) {
        viewModelScope.launch {
            val currentList = parkingConfigs.value.toMutableList()
            currentList.removeAll { it.id == config.id }
            dataManager.saveParkingConfigs(currentList)
        }
    }

    // New function to handle voice commands
    fun payFromVoiceCommand(city: String?, zone: String?) {
        if (city.isNullOrBlank() || zone.isNullOrBlank()) return

        viewModelScope.launch {
            val configs = parkingConfigs.value
            val match = configs.find { config ->
                config.cityName.contains(city, ignoreCase = true) &&
                        config.zoneName.contains(zone, ignoreCase = true)
            }

            if (match != null) {
                _paymentTrigger.emit(match)
            }
        }
    }
}