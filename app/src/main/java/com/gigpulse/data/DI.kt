package com.gigpulse.data
import android.content.Context
import androidx.room.Room
import com.gigpulse.model.AppDb

object DI {
    lateinit var db: AppDb
    fun init(context: Context) {
        db = Room.databaseBuilder(context, AppDb::class.java, "gigpulse.db").build()
    }
}