package qa.deals.doha.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DealEntity::class],
    version = 8,  // ⚠️ CRITICAL: Incremented version to 8
    exportSchema = false
)
abstract class DealDatabase : RoomDatabase() {
    abstract fun dealDao(): DealDao

    companion object {
        // ✅ Migration from version 3 to 4 (adds indices)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_title` ON `deals` (`title`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_createdAt` ON `deals` (`createdAt`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_status` ON `deals` (`status`)")
            }
        }

        // ✅ Migration 4 to 5 (add category and postedBy)
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE deals ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
                database.execSQL("ALTER TABLE deals ADD COLUMN postedBy TEXT NOT NULL DEFAULT 'Anonymous'")
                Log.d("DealDatabase", "✅ Migration 4→5: Added category & postedBy columns")
            }
        }

        // ✅ Migration 5 to 6 (adds autoApproved)
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE deals ADD COLUMN autoApproved INTEGER NOT NULL DEFAULT 0")
                Log.d("DealDatabase", "✅ Migration 5→6: Added autoApproved column")
            }
        }

        // ========================================
        // ✅ NEW: MIGRATION 6 to 7
        // (Adds the missing index for 'category')
        // ========================================
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_category` ON `deals` (`category`)")
                Log.d("DealDatabase", "✅ Migration 6→7: Added category index")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE deals ADD COLUMN promoCode TEXT DEFAULT NULL")
                Log.d("DealDatabase", "Migration 7->8: Added promoCode column")
            }
        }

    }
}