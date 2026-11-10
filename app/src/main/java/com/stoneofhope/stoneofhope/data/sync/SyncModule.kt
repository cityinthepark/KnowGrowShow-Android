package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.crypto.KeystoreNoteKeyCache
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyCache
import com.stoneofhope.stoneofhope.data.crypto.NoteKeyProvider
import com.stoneofhope.stoneofhope.data.remote.NoteImageCloud
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.firestore.FirebaseNoteImageCloud
import com.stoneofhope.stoneofhope.data.remote.firestore.FirestoreNoteKeyProvider
import com.stoneofhope.stoneofhope.data.remote.firestore.FirestoreRemoteStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-lifetime scope for work that must outlive the screen or lifecycle callback that started it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindings {
    @Binds abstract fun remoteStore(impl: FirestoreRemoteStore): RemoteStore
    @Binds abstract fun noteImageCloud(impl: FirebaseNoteImageCloud): NoteImageCloud
    @Binds abstract fun noteKeyProvider(impl: FirestoreNoteKeyProvider): NoteKeyProvider
    @Binds abstract fun noteKeyCache(impl: KeystoreNoteKeyCache): NoteKeyCache
    @Binds abstract fun syncIdentity(impl: FirebaseSyncIdentity): SyncIdentity
    @Binds abstract fun networkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
