package qa.deals.doha.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DealEntity::class,
        UserEntity::class
    ],

    version = 11,  // Change from 10 to 11
    exportSchema = false
)

abstract class DealDatabase : RoomDatabase() {
    abstract fun dealDao(): DealDao
    abstract fun userDao(): UserDao  // Add this


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
        // ========================================
        // ✅ SPRINT 1: MIGRATION 8 to 9
        // (Adds isArchived column and index for archive feature)
        // ========================================
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add isArchived column with default value false (all existing deals remain active)
                database.execSQL("ALTER TABLE deals ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                // Add index for fast filtering of active vs archived deals
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_deals_isArchived` ON `deals` (`isArchived`)")
                Log.d("DealDatabase", "✅ Migration 8→9: Added isArchived column & index (SPRINT 1: Archive Feature)")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {

            override fun migrate(database: SupportSQLiteDatabase) {

                // Create users table

                database.execSQL("""

            CREATE TABLE IF NOT EXISTS users (

                id TEXT PRIMARY KEY NOT NULL,

                email TEXT NOT NULL,

                username TEXT NOT NULL,

                device_id TEXT,

                email_verified INTEGER NOT NULL DEFAULT 0,

                role TEXT NOT NULL DEFAULT 'user',

                auto_approve INTEGER NOT NULL DEFAULT 0,

                approved_deals_count INTEGER NOT NULL DEFAULT 0,

                created_at TEXT,

                last_login_at TEXT

            )

        """)



                // Add new columns to deals table

                database.execSQL("ALTER TABLE deals ADD COLUMN submitted_by_user_id TEXT")

                database.execSQL("ALTER TABLE deals ADD COLUMN approved_by TEXT")

                database.execSQL("ALTER TABLE deals ADD COLUMN approved_at TEXT")

                database.execSQL("ALTER TABLE deals ADD COLUMN report_count INTEGER NOT NULL DEFAULT 0")

                database.execSQL("ALTER TABLE deals ADD COLUMN deleted_at TEXT")

                database.execSQL("ALTER TABLE deals ADD COLUMN deleted_by TEXT")

                database.execSQL("ALTER TABLE deals ADD COLUMN deletion_reason TEXT")



                // Create indices

                database.execSQL("CREATE INDEX IF NOT EXISTS index_deals_submitted_by_user_id ON deals(submitted_by_user_id)")

                database.execSQL("CREATE INDEX IF NOT EXISTS index_deals_approved_by ON deals(approved_by)")

                database.execSQL("CREATE INDEX IF NOT EXISTS index_deals_deleted_at ON deals(deleted_at)")

            }

        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {

                // SQLite doesn't support modifying column constraints directly
                // We need to recreate the table with nullable email/username
                // 1. Create new table with nullable email and username
                database.execSQL("""

                    CREATE TABLE IF NOT EXISTS users_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        email TEXT,
                        username TEXT,
                        device_id TEXT,
                        email_verified INTEGER NOT NULL DEFAULT 0,
                        role TEXT NOT NULL DEFAULT 'user',
                        auto_approve INTEGER NOT NULL DEFAULT 0,
                        approved_deals_count INTEGER NOT NULL DEFAULT 0,
                        created_at TEXT,
                        last_login_at TEXT
                    )
                """)
                // 2. Copy data from old table to new table

                database.execSQL("""
                    INSERT INTO users_new (id, email, username, device_id, email_verified,
                                          role, auto_approve, approved_deals_count,
                                          created_at, last_login_at)
                    SELECT id, email, username, device_id, email_verified,
                           role, auto_approve, approved_deals_count,
                           created_at, last_login_at
                    FROM users
                """)
                // 3. Drop old table
                database.execSQL("DROP TABLE users")
                // 4. Rename new table to original name
                database.execSQL("ALTER TABLE users_new RENAME TO users")
                Log.d("DealDatabase", "✅ Migration 10→11: Made email and username nullable in users table")

            }

        }


    }
}