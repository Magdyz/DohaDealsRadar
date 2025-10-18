package qa.deals.doha.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DealEntity::class],
    version = 5,  //
    exportSchema = false
)
abstract class DealDatabase : RoomDatabase() {
    abstract fun dealDao(): DealDao

    companion object {
        // ✅ Migration from version 3 to 4 (adds indices)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add indices for better query performance
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_title` ON `deals` (`title`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_createdAt` ON `deals` (`createdAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_status` ON `deals` (`status`)")
            }
        }

        // ✨ NEW: Migration 4 to 5 (add category column)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add category column with default value
                database.execSQL("ALTER TABLE deals ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
                Log.d("DealDatabase", "✅ Migration 4→5: Added category column")
            }
        }

    }
}