package qa.deals.doha.db

import android.content.Context
import androidx.room.Room

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
                    DealDatabase.MIGRATION_3_4,  // ✅ Existing migration
                    DealDatabase.MIGRATION_4_5   // ✨ NEW: Category migration
                )
                .fallbackToDestructiveMigration()  // Keep this as safety net
                .build()
                .also { INSTANCE = it }
        }
    }

    fun provideDealDao(context: Context): DealDao =
        provideDatabase(context).dealDao()
}