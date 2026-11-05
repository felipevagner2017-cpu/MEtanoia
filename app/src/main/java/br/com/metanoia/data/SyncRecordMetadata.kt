package br.com.metanoia.data
import androidx.room.*
@Entity(tableName="sync_record_metadata",primaryKeys=["entityType","localId"],indices=[Index(value=["recordId"],unique=true),Index(value=["syncState","updatedAt"])])
data class SyncRecordMetadata(val entityType:String,val localId:String,val recordId:String,val dimension:String,val recordType:String,val revision:Long=0L,val syncState:String="local",val sourceDeviceId:String?=null,val encryptionVersion:Int=0,val remoteUpdatedAt:Long=0L,val updatedAt:Long=System.currentTimeMillis())
@Entity(tableName="sync_cursors",primaryKeys=["accountId","deviceId"])
data class SyncCursorEntity(val accountId:String,val deviceId:String,val serverUpdatedAt:Long=0L,val afterRecordId:String="",val updatedAt:Long=System.currentTimeMillis())
@Dao interface SyncRecordMetadataDao {
 @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:SyncRecordMetadata)
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsert(value:SyncRecordMetadata)
 @Query("SELECT * FROM sync_record_metadata WHERE entityType=:entityType AND localId=:localId LIMIT 1") suspend fun find(entityType:String,localId:String):SyncRecordMetadata?
 @Query("SELECT * FROM sync_record_metadata WHERE recordId=:recordId LIMIT 1") suspend fun findByRecordId(recordId:String):SyncRecordMetadata?
 @Query("SELECT * FROM sync_record_metadata WHERE syncState IN ('local','pending','conflict') ORDER BY updatedAt") suspend fun pendingOrConflicted():List<SyncRecordMetadata>
 @Query("UPDATE sync_record_metadata SET revision=:revision,syncState='synced',remoteUpdatedAt=:remoteUpdatedAt,updatedAt=:now WHERE recordId=:recordId AND syncState!='pending'") suspend fun markSyncedIfNoPending(recordId:String,revision:Long,remoteUpdatedAt:Long,now:Long=System.currentTimeMillis()):Int
 @Query("UPDATE sync_record_metadata SET syncState='conflict',updatedAt=:now WHERE recordId=:recordId") suspend fun markConflict(recordId:String,now:Long=System.currentTimeMillis()):Int
 @Query("SELECT COUNT(*) FROM sync_record_metadata") suspend fun count():Int
}
@Dao interface SyncCursorDao {
 @Query("SELECT * FROM sync_cursors WHERE accountId=:accountId AND deviceId=:deviceId LIMIT 1") suspend fun get(accountId:String,deviceId:String):SyncCursorEntity?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(cursor:SyncCursorEntity)
}
