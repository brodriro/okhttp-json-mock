package io.github.brodriro.okhttpjsonmock.sample

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.brodriro.okhttpjsonmock.OkHttpMockInterceptor
import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import io.github.brodriro.okhttpjsonmock.sample.models.UserModel
import io.github.brodriro.okhttpjsonmock.sample.services.UsersService
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Response
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

        lifecycleScope.launch {
            loadUsers()
            showOneUrlPerMethod(findViewById(R.id.calls_log))
        }
    }

    /** Calls the same resource with different HTTP methods and lists what each mock answered. */
    private suspend fun showOneUrlPerMethod(log: TextView) {
        val ana = UserModel(id = 4, name = "Ana", lastName = "Diaz", age = 31, phoneNumbers = emptyList())
        val calls = listOf<Pair<String, suspend () -> Response<*>>>(
            "POST /users" to { usersService.createUser(ana) },
            "PUT /users/1" to { usersService.updateUser(1, ana) },
            "DELETE /users/1" to { usersService.deleteUser(1) },
            "GET /users/9" to { usersService.findUser(9) },
        )
        val lines = mutableListOf<String>()
        for ((name, call) in calls) {
            lines += try {
                val response = call()
                val location = response.headers()["Location"]?.let { ", Location: $it" }.orEmpty()
                getString(R.string.call_result, name, response.code(), response.message(), location)
            } catch (e: IOException) {
                getString(R.string.call_failed, name, e.message)
            }
            log.text = lines.joinToString("\n")
        }
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
