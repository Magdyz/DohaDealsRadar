package qa.deals.doha.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DealEntity::class],
    version = 4,  // ✅ Bumped from 3 to 4
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
    }
}