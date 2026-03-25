package dev.russell.fatebook.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    // QuestionRepository is constructor-injected with @Singleton @Inject,
    // so Hilt provides it automatically — no explicit binding needed.
    // This module is a placeholder for future bindings (e.g., interface→impl).
}
