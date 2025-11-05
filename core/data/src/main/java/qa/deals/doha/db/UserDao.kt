package qa.deals.doha.db



import androidx.room.*

import kotlinx.coroutines.flow.Flow



@Dao

interface UserDao {



    @Query("SELECT * FROM users WHERE id = :userId")

    suspend fun getUserById(userId: String): UserEntity?



    @Query("SELECT * FROM users WHERE id = :userId")

    fun getUserByIdFlow(userId: String): Flow<UserEntity?>



    @Query("SELECT * FROM users WHERE email = :email")

    suspend fun getUserByEmail(email: String): UserEntity?



    @Query("SELECT role FROM users WHERE id = :userId")

    suspend fun getUserRole(userId: String): String?



    @Query("SELECT role FROM users WHERE id = :userId")

    fun getUserRoleFlow(userId: String): Flow<String?>



    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertUser(user: UserEntity)



    @Update

    suspend fun updateUser(user: UserEntity)



    @Query("UPDATE users SET role = :role WHERE id = :userId")

    suspend fun updateUserRole(userId: String, role: String)



    @Query("UPDATE users SET auto_approve = :autoApprove WHERE id = :userId")

    suspend fun updateAutoApprove(userId: String, autoApprove: Boolean)



    @Query("DELETE FROM users WHERE id = :userId")

    suspend fun deleteUser(userId: String)



    @Query("DELETE FROM users")

    suspend fun deleteAllUsers()

}

