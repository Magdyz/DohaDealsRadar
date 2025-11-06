package qa.deals.doha.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import qa.deals.doha.db.DatabaseModule
import qa.deals.doha.db.UserDao
import qa.deals.doha.db.UserEntity
import qa.deals.doha.network.*
import qa.deals.doha.util.AppContext

/**
 * Repository for managing users and authentication
 * Implements local caching with Room database
 */
class UserRepository {

    // Access database and API
    private val userDao: UserDao by lazy {
        DatabaseModule.provideUserDao(AppContext.appContext)
    }
    private val api: SupabaseApiService = NetworkModule.api

    // ========================================
    // LOCAL DATABASE OPERATIONS
    // ========================================

    /**
     * Get cached user by ID (from local Room database)
     * @param userId User ID to fetch
     * @return UserEntity or null if not cached
     */
    suspend fun getCachedUser(userId: String): UserEntity? = withContext(Dispatchers.IO) {
        try {
            userDao.getUserById(userId)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting cached user: $userId", e)
            null
        }
    }

    /**
     * Get cached user as Flow (reactive updates)
     * @param userId User ID to observe
     * @return Flow of UserEntity (null if not cached)
     */
    fun getCachedUserFlow(userId: String): Flow<UserEntity?> {
        return userDao.getUserByIdFlow(userId)
    }

    /**
     * Get cached user role by ID
     * @param userId User ID
     * @return Role string or null
     */
    suspend fun getCachedUserRole(userId: String): String? = withContext(Dispatchers.IO) {
        try {
            userDao.getUserRole(userId)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting cached user role: $userId", e)
            null
        }
    }

    /**
     * Get cached user role as Flow (reactive updates)
     * @param userId User ID
     * @return Flow of role string
     */
    fun getCachedUserRoleFlow(userId: String): Flow<String?> {
        return userDao.getUserRoleFlow(userId)
    }

    /**
     * Cache a user locally (save to Room database)
     * @param user UserDto from network
     */
    suspend fun cacheUser(user: UserDto) = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Caching user: ${user.username} (${user.id})")
            userDao.insertUser(user.toEntity())
            Log.d("UserRepository", "User cached successfully")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error caching user: ${user.id}", e)
            throw e
        }
    }

    /**
     * Cache a user entity directly
     * @param userEntity UserEntity to cache
     */
    suspend fun cacheUserEntity(userEntity: UserEntity) = withContext(Dispatchers.IO) {
        try {
            userDao.insertUser(userEntity)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error caching user entity", e)
            throw e
        }
    }

    /**
     * Update user role in local cache
     * @param userId User ID
     * @param role New role
     */
    suspend fun updateCachedUserRole(userId: String, role: String) = withContext(Dispatchers.IO) {
        try {
            userDao.updateUserRole(userId, role)
            Log.d("UserRepository", "Updated user role: $userId -> $role")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating cached user role", e)
            throw e
        }
    }

    /**
     * Clear all cached users
     */
    suspend fun clearUserCache() = withContext(Dispatchers.IO) {
        try {
            userDao.deleteAllUsers()
            Log.d("UserRepository", "User cache cleared")
        } catch (e: Exception) {
            Log.e("UserRepository", "Error clearing user cache", e)
            throw e
        }
    }

    // ========================================
    // NETWORK OPERATIONS
    // ========================================

    /**
     * Fetch user profile from API and cache it locally
     * @param userId User ID to fetch
     * @return Result with UserDto or error
     */
    suspend fun fetchUserProfile(userId: String): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            Log.d("UserRepository", "Fetching user profile: $userId")

            val response = api.getUserProfile(
                GetUserProfileRequest(userId = userId)
            )

            if (response.success == true && response.data != null) {
                // Cache the user locally
                cacheUser(response.data)

                Log.d("UserRepository", "User profile fetched and cached: ${response.data.username}")
                Result.success(response.data)
            } else {
                Log.e("UserRepository", "Failed to fetch user profile: ${response.error}")
                Result.failure(Exception(response.error ?: "Failed to fetch user profile"))
            }
        } catch (e: Exception) {
            Log.e("UserRepository", "Error fetching user profile: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile - tries cache first, then fetches from API
     * @param userId User ID
     * @return Result with UserEntity or error
     */
    suspend fun getUserProfile(userId: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            // Try cache first
            val cachedUser = getCachedUser(userId)
            if (cachedUser != null) {
                Log.d("UserRepository", "Returning cached user: ${cachedUser.username}")
                return@withContext Result.success(cachedUser)
            }

            // Not in cache, fetch from API
            val fetchResult = fetchUserProfile(userId)
            if (fetchResult.isSuccess) {
                val userDto = fetchResult.getOrNull()
                if (userDto != null) {
                    return@withContext Result.success(userDto.toEntity())
                }
            }

            Result.failure(fetchResult.exceptionOrNull() ?: Exception("Failed to get user profile"))
        } catch (e: Exception) {
            Log.e("UserRepository", "Error getting user profile", e)
            Result.failure(e)
        }
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Check if user is moderator or admin
     * @param userId User ID to check
     * @return true if user has moderator or admin role
     */
    suspend fun isModerator(userId: String): Boolean = withContext(Dispatchers.IO) {
        val role = getCachedUserRole(userId)
        role == "moderator" || role == "admin"
    }

    /**
     * Check if user is admin
     * @param userId User ID to check
     * @return true if user has admin role
     */
    suspend fun isAdmin(userId: String): Boolean = withContext(Dispatchers.IO) {
        val role = getCachedUserRole(userId)
        role == "admin"
    }

    /**
     * Check if user has auto-approve privilege
     * @param userId User ID to check
     * @return true if user can auto-approve deals
     */
    suspend fun hasAutoApprove(userId: String): Boolean = withContext(Dispatchers.IO) {
        val user = getCachedUser(userId)
        user?.autoApprove ?: false
    }
}
