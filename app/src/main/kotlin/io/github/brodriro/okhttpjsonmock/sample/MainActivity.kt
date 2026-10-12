package io.github.brodriro.okhttpjsonmock.sample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.sample.services.UsersService
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val usersAdapter = UsersAdapter()
    private val usersService: UsersService by lazy { createService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<RecyclerView>(R.id.recyclerview).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = usersAdapter
        }

        lifecycleScope.launch { loadUsers() }
    }

    private suspend fun loadUsers() {
        try {
            usersAdapter.submitList(usersService.getUsers(page = 1))
        } catch (e: IOException) {
            Log.e(TAG, "Could not load the users", e)
        } catch (e: HttpException) {
            Log.e(TAG, "The users request answered ${e.code()}", e)
        }
    }

    private fun createService(): UsersService {
        val client = OkHttpClient.Builder()
            .addInterceptor(
                OkHttpMockInterceptor.Builder(assetsProvider())
                    .delay(MIN_DELAY_MILLIS, MAX_DELAY_MILLIS)
                    .build(),
            )
            .build()
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsersService::class.java)
    }

    /** Reads the mocks from the `assets` folder of the app. */
    private fun assetsProvider() = InputStreamProvider { path ->
        try {
            assets.open(path)
        } catch (e: IOException) {
            // No asset at this path: the interceptor moves on to its next candidate.
            null
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val BASE_URL = "http://example.com"
        const val MIN_DELAY_MILLIS = 200
        const val MAX_DELAY_MILLIS = 600
    }
}
