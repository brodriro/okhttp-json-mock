package io.github.brodriro.okhttpjsonmock.sample.models

data class UserModel(
    val id: Int,
    val name: String,
    val lastName: String,
    val age: Int,
    // Gson leaves this null when the mock has no "phoneNumbers" field.
    val phoneNumbers: List<String>?,
)
