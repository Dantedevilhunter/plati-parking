package com.llitc.platiparking

import kotlinx.serialization.Serializable

@Serializable
data class ParkingConfig(
    val id: Long = System.currentTimeMillis(), // Unique ID for each config
    val cityName: String,
    val zoneName: String,
    val smsNumber: String
)