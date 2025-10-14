package qa.deals.doha.repository

import qa.deals.doha.db.DealDao
import qa.deals.doha.db.DealEntity
import kotlinx.coroutines.flow.Flow

class DealLocalDataSource(private val dao: DealDao) {

    fun getDeals(): Flow<List<DealEntity>> = dao.getAllDeals()

    suspend fun saveDeals(deals: List<DealEntity>) = dao.insertAll(deals)

    suspend fun clearDeals() = dao.clearAll()
}
