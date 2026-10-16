package io.github.brodriro.okhttpjsonmock.sample

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.brodriro.okhttpjsonmock.sample.catalog.Example
import io.github.brodriro.okhttpjsonmock.sample.mock.Exchange
import io.github.brodriro.okhttpjsonmock.sample.mock.NetworkSettings
import io.github.brodriro.okhttpjsonmock.sample.mock.Playground
import io.github.brodriro.okhttpjsonmock.sample.mock.SampleRequest
import io.github.brodriro.okhttpjsonmock.sample.models.UserModel
import io.github.brodriro.okhttpjsonmock.sample.services.UsersService
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/** State of the sample: settings, the result of each example and the history of calls. */
class SampleViewModel(application: Application) : AndroidViewModel(application) {

    val playground = Playground(application.assets)

    var settings by mutableStateOf(NetworkSettings())
        private set

    private val results = mutableStateMapOf<String, Exchange>()
    private val running = mutableStateMapOf<String, Boolean>()

    /** Every exchange of the session, newest first. */
    val history = mutableStateListOf<Exchange>()

    var retrofit by mutableStateOf(RetrofitState())
        private set

    fun result(example: Example): Exchange? = results[example.id]

    fun isRunning(example: Example): Boolean = running[example.id] == true

    fun updateSettings(settings: NetworkSettings) {
        this.settings = settings
    }

    fun send(example: Example) {
        if (isRunning(example)) return
        running[example.id] = true
        viewModelScope.launch {
            val exchange = send(example.request)
            results[example.id] = exchange
            running[example.id] = false
        }
    }

    /** Sends a request outside the catalog, for example the bursts of the network screen. */
    suspend fun send(request: SampleRequest): Exchange {
        val exchange = playground.send(request, settings)
        history.add(0, exchange)
        return exchange
    }

    fun clearHistory() {
        history.clear()
    }

    /** Runs the calls of the Retrofit screen through a service built on the current settings. */
    fun loadRetrofit() {
        retrofit = RetrofitState(loading = true)
        viewModelScope.launch {
            val service = usersService()
            val users = try {
                service.getUsers(page = 1)
            } catch (e: IOException) {
                retrofit = RetrofitState(error = e.message)
                return@launch
            } catch (e: HttpException) {
                retrofit = RetrofitState(error = "HTTP ${e.code()}")
                return@launch
            }
            val created = try {
                service.createUser(ANA).let { "${it.code()} · Location: ${it.headers()["Location"]}" }
            } catch (e: IOException) {
                e.message
            }
            val missing = try {
                service.findUser(9).let { "${it.code()} · ${it.errorBody()?.string()}" }
            } catch (e: IOException) {
                e.message
            }
            retrofit = RetrofitState(users = users, created = created, missing = missing)
        }
    }

    private fun usersService(): UsersService {
        val client = OkHttpClient.Builder().addInterceptor(playground.interceptor(settings)).build()
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UsersService::class.java)
    }

    private companion object {
        val ANA = UserModel(id = 4, name = "Ana", lastName = "Diaz", age = 31, phoneNumbers = emptyList())
    }
}

/** Results of the Retrofit screen. */
data class RetrofitState(
    val loading: Boolean = false,
    val users: List<UserModel> = emptyList(),
    val created: String? = null,
    val missing: String? = null,
    val error: String? = null,
)
