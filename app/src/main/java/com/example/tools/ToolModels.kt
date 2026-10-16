package com.example.tools

enum class ToolResultStatus {
    SUCCESS,
    PERMISSION_REQUIRED,
    USER_CONFIRMATION_REQUIRED,
    UNSUPPORTED,
    RETRYABLE_ERROR,
    PERMANENT_ERROR
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val requiredPermissions: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false
)

data class ToolParameter(
    val name: String,
    val type: String, // "string", "number", "boolean"
    val description: String,
    val required: Boolean = true
)

data class ToolExecutionResult(
    val status: ToolResultStatus,
    val message: String,
    val data: Map<String, String>? = null,
    val missingPermission: String? = null
)
