package at.fitnessplatform.core.datastore

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecretStoreModule {
    @Binds
    @Singleton
    abstract fun bindGuestSecretStore(implementation: KeystoreGuestSecretStore): GuestSecretStore
}
