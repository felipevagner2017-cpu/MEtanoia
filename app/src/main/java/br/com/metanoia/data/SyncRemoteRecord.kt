package br.com.metanoia.data
import androidx.room.*
@Entity(tableName="sync_remote_records",indices=[Index(value=["serverUpdatedAt","recordId"])])
data class SyncRemoteRecord(@PrimaryKey val recordId:String,val dimension:String,val recordType:String,val payloadCiphertext:String,val encryptionVersion:Int,val revision:Long,val clientUpdatedAt:Long,val serverUpdatedAt:Long,val deletedAt:Long?,val sourceDeviceId:String?,val status:String="received",val lastError:String?=null)
@Dao interface SyncRemoteRecordDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(record:SyncRemoteRecord):Long
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun replace(record:SyncRemoteRecord)
 @Query("SELECT * FROM sync_remote_records WHERE status IN ('received','retry','conflict') ORDER BY serverUpdatedAt,recordId LIMIT :limit") suspend fun pending(limit:Int=100):List<SyncRemoteRecord>
 @Query("UPDATE sync_remote_records SET status='applied',lastError=NULL WHERE recordId=:recordId AND revision=:revision") suspend fun markApplied(recordId:String,revision:Long):Int
 @Query("UPDATE sync_remote_records SET status='conflict',lastError=:reason WHERE recordId=:recordId") suspend fun markConflict(recordId:String,reason:String):Int
 @Query("SELECT * FROM sync_remote_records WHERE recordId=:recordId LIMIT 1") suspend fun find(recordId:String):SyncRemoteRecord?
}
