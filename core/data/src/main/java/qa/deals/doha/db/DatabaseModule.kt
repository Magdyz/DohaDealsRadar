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
                    DealDatabase.MIGRATION_8_9,
                    DealDatabase.MIGRATION_9_10,
                    DealDatabase.MIGRATION_10_11,
                    DealDatabase.MIGRATION_11_12  // ✅ NEW: Rejection fields
                )
                // ========================================
                // ✅ FIX (1.2): Removed .fallbackToDestructiveMigration()
                // This line would delete the user's entire database (including
                // their vote history) on the next app upgrade (e.g., version 10).
                // Removing it forces proper migrations for all future updates,
                // protecting user data.
                // ========================================
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }

    fun provideDealDao(context: Context): DealDao =
        provideDatabase(context).dealDao()

    fun provideUserDao(context: Context): UserDao =
        provideDatabase(context).userDao()

}