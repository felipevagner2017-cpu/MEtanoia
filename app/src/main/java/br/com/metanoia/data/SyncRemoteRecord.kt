package br.com.metanoia.data
import androidx.room.*

@Entity(tableName="sync_remote_records",primaryKeys=["accountId","recordId"],indices=[Index(value=["accountId","serverUpdatedAt","recordId"])])
data class SyncRemoteRecord(
 @ColumnInfo(defaultValue="''") val accountId:String,
 val recordId:String,
 val dimension:String,
 val recordType:String,
 val payloadCiphertext:String,
 val encryptionVersion:Int,
 val revision:Long,
 val clientUpdatedAt:Long,
 val serverUpdatedAt:Long,
 val deletedAt:Long?,
 val sourceDeviceId:String?,
 val status:String="received",
 val lastError:String?=null
)
@Dao interface SyncRemoteRecordDao {
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(record:SyncRemoteRecord):Long
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun replace(record:SyncRemoteRecord)
 @Query("SELECT * FROM sync_remote_records WHERE accountId=:accountId AND status IN ('received','retry','conflict') ORDER BY serverUpdatedAt,recordId LIMIT :limit") suspend fun pending(accountId:String,limit:Int=100):List<SyncRemoteRecord>
 @Query("UPDATE sync_remote_records SET status='applied',lastError=NULL WHERE accountId=:accountId AND recordId=:recordId AND revision=:revision") suspend fun markApplied(accountId:String,recordId:String,revision:Long):Int
 @Query("UPDATE sync_remote_records SET status='conflict',lastError=:reason WHERE accountId=:accountId AND recordId=:recordId") suspend fun markConflict(accountId:String,recordId:String,reason:String):Int
 @Query("SELECT * FROM sync_remote_records WHERE accountId=:accountId AND recordId=:recordId LIMIT 1") suspend fun find(accountId:String,recordId:String):SyncRemoteRecord?
}
