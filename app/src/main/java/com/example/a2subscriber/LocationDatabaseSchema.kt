package com.example.a2subscriber

object LocationDatabaseSchema {
    const val DATABASE_NAME = "SubscriberData.db"
    const val DATABASE_VERSION = 1

    object LocationEntry {
        const val TABLE_NAME = "LocationData"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_TIMESTAMP = "timestamp"
    }
}

