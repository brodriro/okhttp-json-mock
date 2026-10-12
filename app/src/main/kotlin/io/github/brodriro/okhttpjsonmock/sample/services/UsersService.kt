package io.github.brodriro.okhttpjsonmock.sample.services

import io.github.brodriro.okhttpjsonmock.sample.models.UserModel
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Each call is answered by the mock file named in its comment, under `assets/`. */
interface UsersService {

    /** `api/v1/users/page=1.json` */
    @GET("$API/users")
    suspend fun getUsers(@Query("page") page: Int): List<UserModel>

    /** `api/v1/users/1.json` */
    @GET("$API/users/{userId}")
    suspend fun getUser(@Path("userId") userId: Int): UserModel

    /** `api/v1/users/1/phoneNumbers.json` */
    @GET("$API/users/{userId}/phoneNumbers")
    suspend fun getUserNumbers(@Path("userId") userId: Int): List<String>

    companion object {
        const val API = "api/v1"
    }
}
