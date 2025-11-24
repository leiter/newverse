package com.together.newverse.di

import org.koin.core.module.Module

/**
 * Expected flavor-specific appModule
 * This is provided by buyMain or sellMain source sets
 */
expect val flavorAppModule: Module
