package org.dylanjones.sleepradio.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.dylanjones.sleepradio.core.data.SettingsRepository
import org.dylanjones.sleepradio.core.data.SettingsRepositoryImpl
import org.dylanjones.sleepradio.core.data.db.AudiobookProgressDao
import org.dylanjones.sleepradio.core.data.db.PodcastFeedDao
import org.dylanjones.sleepradio.core.data.db.PodcastProgressDao
import org.dylanjones.sleepradio.core.data.db.SleepRadioDatabase
import org.dylanjones.sleepradio.core.data.db.SourceSlotDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun providePreferencesDataStore(
            @ApplicationContext context: Context,
            @IoDispatcher io: kotlinx.coroutines.CoroutineDispatcher,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + io),
            produceFile = { context.preferencesDataStoreFile("sleepradio_settings") },
        )

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SleepRadioDatabase =
            Room.databaseBuilder(context, SleepRadioDatabase::class.java, "sleepradio.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        @Provides
        fun provideSourceSlotDao(db: SleepRadioDatabase): SourceSlotDao = db.sourceSlotDao()

        @Provides
        fun provideAudiobookProgressDao(db: SleepRadioDatabase): AudiobookProgressDao =
            db.audiobookProgressDao()

        @Provides
        fun providePodcastFeedDao(db: SleepRadioDatabase): PodcastFeedDao = db.podcastFeedDao()

        @Provides
        fun providePodcastProgressDao(db: SleepRadioDatabase): PodcastProgressDao =
            db.podcastProgressDao()
    }
}
