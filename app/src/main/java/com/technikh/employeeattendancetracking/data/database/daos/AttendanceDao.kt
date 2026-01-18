package com.technikh.employeeattendancetracking.data.database.daos


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.technikh.employeeattendancetracking.data.database.entities.AttendanceRecord
import com.technikh.employeeattendancetracking.data.database.entities.DayOfficeHours
import com.technikh.employeeattendancetracking.data.database.entities.Employee
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM employees")
    suspend fun getAllEmployeesList(): List<Employee>

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    suspend fun getAllRecordsList(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE employeeId = :empId ORDER BY timestamp DESC LIMIT 1")
    fun getLastRecordFlow(empId: String): Flow<AttendanceRecord?>

    @Query("SELECT * FROM attendance_records WHERE employeeId = :empId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRecord(empId: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayAttendance(startOfDay: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records ORDER BY timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<AttendanceRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId ORDER BY timestamp DESC")
     fun getAttendanceByEmployee(employeeId: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE employeeId = :employeeId ORDER BY timestamp DESC")
    fun getDailyAttendance(employeeId: String): Flow<List<AttendanceRecord>>

    @Query("""
        SELECT 
            date(timestamp/1000, 'unixepoch') as day,
            SUM(CASE WHEN punchType = 'OUT' AND isOfficeWork = 1 THEN 
                (timestamp - (SELECT timestamp FROM attendance_records r2 
                              WHERE r2.employeeId = r1.employeeId 
                              AND date(r2.timestamp/1000, 'unixepoch') = date(r1.timestamp/1000, 'unixepoch')
                              AND r2.punchType = 'IN' 
                              AND r2.id < r1.id
                              ORDER BY r2.timestamp DESC LIMIT 1)
                ) 
                ELSE 0 END) / (1000 * 60 * 60.0) as officeHours
        FROM attendance_records r1
        WHERE employeeId = :employeeId 
        AND strftime('%Y-%m', datetime(timestamp/1000, 'unixepoch')) = :monthYear
        GROUP BY date(timestamp/1000, 'unixepoch')
    """)
    suspend fun getMonthlyOfficeHours(employeeId: String, monthYear: String): List<DayOfficeHours>
}