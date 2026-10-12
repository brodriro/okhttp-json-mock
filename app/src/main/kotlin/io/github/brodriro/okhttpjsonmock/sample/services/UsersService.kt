package io.github.brodriro.okhttpjsonmock.sample.services

import io.github.brodriro.okhttpjsonmock.sample.models.UserModel
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
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

    /** `api/v1/users/9.get.json`, a mocked 404. A [Response] exposes the status code instead of throwing. */
    @GET("$API/users/{userId}")
    suspend fun findUser(@Path("userId") userId: Int): Response<UserModel>

    /** `api/v1/users.post.json`: same URL as [getUsers], different HTTP method. Answers 201 with a `Location`. */
    @POST("$API/users")
    suspend fun createUser(@Body user: UserModel): Response<UserModel>

    /** `api/v1/users/1.put.json` */
    @PUT("$API/users/{userId}")
    suspend fun updateUser(@Path("userId") userId: Int, @Body user: UserModel): Response<UserModel>

    /** `api/v1/users/1.delete.json`, which answers 204 with no body. */
    @DELETE("$API/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: Int): Response<Unit>

    companion object {
        const val API = "api/v1"
    }
}
