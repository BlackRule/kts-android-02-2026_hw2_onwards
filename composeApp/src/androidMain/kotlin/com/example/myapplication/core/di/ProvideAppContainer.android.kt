package com.example.myapplication.core.di

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.core.cache.AppCacheDatabase
import com.example.myapplication.core.session.dataStoreSessionRepository
import com.example.myapplication.feature.login.repository.LoginRepository
import com.example.myapplication.feature.shopPicker.repository.RoomShopsCacheDataSource
import com.example.myapplication.feature.shopPicker.repository.ShopsRepository
import com.example.myapplication.feature.shoppingList.presentation.shoppingListItemsLocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun ProvideAppContainer(content: @Composable () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val container = remember(appContext) {
        AndroidAppContainer.getInstance(appContext)
    }

    CompositionLocalProvider(
        LocalAppContainer provides container,
        content = content,
    )
}

private class AndroidAppContainer private constructor(
    context: Context,
) : AppContainer {

    private val appContext = context.applicationContext
    private val cacheDatabase = AppCacheDatabase.getInstance(appContext)
    private val shoppingListItemsLocalStore = shoppingListItemsLocalStore(appContext)

    override val sessionRepository = dataStoreSessionRepository(appContext)
    override val loginRepository = LoginRepository()
    override val shopsRepository = ShopsRepository(
        cacheDataSource = RoomShopsCacheDataSource(cacheDatabase.cachedShopDao()),
    )
    override val appDataCleaner: AppDataCleaner = object : AppDataCleaner {
        override suspend fun clear() {
            withContext(Dispatchers.IO) {
                cacheDatabase.clearAllTables()
                shoppingListItemsLocalStore.clearAll()
            }
        }
    }

    companion object {
        @Volatile
        private var instance: AndroidAppContainer? = null

        fun getInstance(context: Context): AndroidAppContainer {
            return instance ?: synchronized(this) {
                instance ?: AndroidAppContainer(context).also { created ->
                    instance = created
                }
            }
        }
    }
}
