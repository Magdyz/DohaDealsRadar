package qa.deals.doha.db

import android.content.Context
import androidx.room.Room

/**
 * ========================================
 * ✨ DATABASE MODULE (OBJECT)
 * ========================================
 */
object DatabaseModule {
    @Volatile
    private var INSTANCE: DealDatabase? = null

    fun provideDatabase(context: Context): DealDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                DealDatabase::class.java,
                "deals.db"
            )
                .addMigrations(
                    DealDatabase.MIGRATION_3_4,
                    DealDatabase.MIGRATION_4_5,
                    DealDatabase.MIGRATION_5_6,
                    DealDatabase.MIGRATION_6_7,
                    DealDatabase.MIGRATION_7_8,
                    DealDatabase.MIGRATION_8_9  // ✅ SPRINT 1: Archive feature migration
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }

    // ========================================
    // ✅ FIX: Renamed this function from 'provideDao'
    // to 'provideDealDao' to fix the unresolved reference.
    // ========================================
    fun provideDealDao(context: Context): DealDao =
        provideDatabase(context).dealDao()
}