package com.pennywiseai.tracker.di

import com.pennywiseai.tracker.billing.EntitlementSource
import com.pennywiseai.tracker.billing.FdroidBillingGateway
import com.pennywiseai.tracker.billing.PurchaseGateway
import com.pennywiseai.tracker.billing.PurchaseLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * This personal build uses the same always-Pro gateway as the F-Droid flavor.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindPurchaseGateway(impl: FdroidBillingGateway): PurchaseGateway

    @Binds
    @Singleton
    abstract fun bindEntitlementSource(impl: FdroidBillingGateway): EntitlementSource

    @Binds
    @Singleton
    abstract fun bindPurchaseLauncher(impl: FdroidBillingGateway): PurchaseLauncher
}
