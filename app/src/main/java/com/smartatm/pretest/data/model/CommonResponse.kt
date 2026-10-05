package com.smartatm.pretest.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DataWrapper<T>(
    val data: T
)

@Serializable
data class ErrorResponse(
    val detail: ErrorDetail? = null
)

@Serializable
data class ErrorDetail(
    val code: String? = null,
    val message: String? = null
)
