package dev.tvtuner.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** App-level Hilt module — most bindings live in their own feature/module DI files. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
