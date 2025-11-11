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
                    DealDatabase.MIGRATION_11_12,  // ✅ Rejection fields
                    DealDatabase.MIGRATION_12_13   // ✅ NEW: Deal expiration (expires_at)
                )

                // ========================================
                // ✅ FIX (1.1.6): Re-enabled .fallbackToDestructiveMigration()
                // This prevents crashes for users on ancient database versions (v1-2)
                // that predate our migration history. Users on v3+ will use the
                // proper migration path and keep their data. Only affects <1% of users
                // on very old versions - better to reset their DB than crash the app.
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