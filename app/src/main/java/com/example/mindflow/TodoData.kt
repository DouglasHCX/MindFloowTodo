package com.example.mindflow

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

// =======================
// 1. 数据实体 (Entity)
// =======================
@Entity(tableName = "todo_table")
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val priority: Int,
    val isDone: Boolean = false,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val category: String = "其他",
    val imageUris: List<String> = emptyList(),
    val completedAt: Long? = null,
    val isIncubated: Boolean = false,
    val isFromInspiration: Boolean = false,

    // ↓↓↓ 新增：录音文件路径 ↓↓↓
    val audioPath: String? = null
)

// =======================
// 2. 类型转换器 (Converters)
// =======================
class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(separator = "||")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return value.split("||")
    }
}

// =======================
// 3. 数据访问对象 (DAO)
// =======================
@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_table ORDER BY id DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem): Long

    @Update
    suspend fun updateTodo(todo: TodoItem)

    @Delete
    suspend fun deleteTodo(todo: TodoItem)

    @Query("SELECT * FROM todo_table")
    fun getAllTodosSync(): List<TodoItem>
}

// =======================
// 4. 数据库核心 (Database)
// =======================
// 注意：版本号升级为 6
@Database(entities = [TodoItem::class], version = 6, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mindflow_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}