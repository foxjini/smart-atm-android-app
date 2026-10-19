package com.smartatm.pretest.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DataWrapper<T>(
    val data: T
)

/**
 * 서버의 실패 응답. 백엔드는 모든 실패를 이 모양으로 보낸다 (FastAPI 기본값인 "detail"이 아니다):
 *   {"error": {"code": "TOKEN_EXPIRED", "message": "로그인이 만료되었습니다. 다시 로그인해 주세요."}}
 */
@Serializable
data class ErrorResponse(
    val error: ErrorDetail? = null
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** 실패 응답 본문에서 오류를 꺼낸다. JSON이 아니거나 모양이 다르면 null (공유기·프록시의 HTML 오류 페이지 등) */
        fun parse(body: String?): ErrorDetail? {
            if (body.isNullOrBlank()) return null
            return try {
                json.decodeFromString<ErrorResponse>(body).error
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Serializable
data class ErrorDetail(
    val code: String? = null,
    val message: String? = null
)
