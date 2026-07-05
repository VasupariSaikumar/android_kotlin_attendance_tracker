package com.technikh.employeeattendancetracking.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.technikh.employeeattendancetracking.data.database.converters.Converters
import com.technikh.employeeattendancetracking.data.database.daos.AttendanceDao
import com.technikh.employeeattendancetracking.data.database.daos.EmployeeDao
import com.technikh.employeeattendancetracking.data.database.daos.WorkReasonDao
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.Employee
import com.technikh.employeeattendancetracking.data.database.entities.OfficeWorkReason


@Database(
    entities = [Employee::class, AttendanceRecord::class, OfficeWorkReason::class],//the 3 classes are defined tables
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun employeeDao(): EmployeeDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun workReasonDao(): WorkReasonDao
//The top 3 functions provide local database connection
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                            CREATE TABLE attendance_records_new (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                employeeId TEXT NOT NULL,
                                punchType TEXT NOT NULL,
                                systemTimeMillis INTEGER NOT NULL,
                                employeeTimeMillis INTEGER NOT NULL,
                                isManuallyEdited INTEGER NOT NULL,
                                reason TEXT,
                                workReason TEXT,
                                isOfficeWork INTEGER NOT NULL,
                                selfiePath TEXT
                            )
                        """
                )
                database.execSQL(
                    """
                            INSERT INTO attendance_records_new (
                                id, employeeId, punchType,
                                systemTimeMillis, employeeTimeMillis, isManuallyEdited,
                                reason, workReason, isOfficeWork, selfiePath
                            )
                            SELECT
                                id, employeeId, punchType,
                                timestamp, timestamp, 0,
                                reason, workReason, isOfficeWork, selfiePath
                            FROM attendance_records
                        """
                )
                database.execSQL(
                    "DROP TABLE attendance_records"
                )
                database.execSQL(
                    "ALTER TABLE attendance_records_new RENAME TO attendance_records" 
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendance_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}