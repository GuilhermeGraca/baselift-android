package com.example.baselift.Model.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.baselift.Model.local.entity.UserEntity
import com.example.baselift.Model.local.dao.UserDao
import com.example.baselift.Model.local.entity.WeightLogEntity
import com.example.baselift.Model.local.dao.WeightLogDao
import com.example.baselift.Model.local.entity.PhotoLogEntity
import com.example.baselift.Model.local.dao.PhotoLogDao
import com.example.baselift.Model.local.entity.WorkoutEntity
import com.example.baselift.Model.local.entity.ExerciseEntity
import com.example.baselift.Model.local.entity.WorkoutSessionEntity
import com.example.baselift.Model.local.entity.SetLogEntity
import com.example.baselift.Model.local.dao.WorkoutDao
import com.example.baselift.Model.local.entity.NutritionLogEntity
import com.example.baselift.Model.local.entity.MealTemplateEntity
import com.example.baselift.Model.local.dao.NutritionDao


/**
 * Base de dados principal
 *
 * Utiliza o padrão Singleton para garantir que existe apenas uma instância
 * da base de dados em toda a aplicação para evitar conflitos de acesso
 * e desperdício de recursos
 */
@Database(
    entities = [
        UserEntity::class, 
        WeightLogEntity::class, 
        PhotoLogEntity::class,
        WorkoutEntity::class,
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        NutritionLogEntity::class,
        MealTemplateEntity::class
    ], // lista de entidades que a base de dados vai ter
                   // cada entidade é uma data class anotada com @Entity

    version = 10, // versão 10 com a adição de índices para performance

    exportSchema = false // false porque não precisamos exportar o esquema para JSON
                         // e assim evitamos configurar a pasta de destino
)

// abstrata porque o Room gera automaticamente
// a implementação concreta em runtime via KSP
// apenas declaramos a estrutura
abstract class AppDatabase : RoomDatabase() {

    // declarações dos DAOs como funções abstratas
    // Dao é a interface que define as queries à base de dados
    // o Room implementa os métodos automaticamente
    abstract fun userDao(): UserDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun photoLogDao(): PhotoLogDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun nutritionDao(): NutritionDao


    // companion object para implementar o singleton
    companion object {

        // migração 7 para 8 adiciona coluna setCount a exercises
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE exercises ADD COLUMN setCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        // migração 8 para 9 adiciona tabelas de nutrição
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `nutrition_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `calories` INTEGER NOT NULL, `protein` INTEGER NOT NULL, `carbs` INTEGER NOT NULL, `fats` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `meal_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `iconName` TEXT NOT NULL, `calories` INTEGER NOT NULL, `protein` INTEGER NOT NULL, `carbs` INTEGER NOT NULL, `fats` INTEGER NOT NULL)")
            }
        }

        // migração 9 para 10 adiciona índices para performance
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_logs_timestamp` ON `weight_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_logs_timestamp` ON `photo_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_nutrition_logs_timestamp` ON `nutrition_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_set_logs_isCompleted` ON `set_logs` (`isCompleted`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_set_logs_timestamp` ON `set_logs` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_isCompleted` ON `workout_sessions` (`isCompleted`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_timestamp` ON `workout_sessions` (`timestamp`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baselift_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
