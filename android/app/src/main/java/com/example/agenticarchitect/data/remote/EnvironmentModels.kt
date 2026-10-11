package com.example.agenticarchitect.data.remote

/**
 * Host development tool metadata (Python, Node, npm, Git).
 */
data class HostToolInfo(
    val installed: Boolean = false,
    val version: String? = null,
    val executable_path: String? = null,
    val major_version: Int? = null,
    val minor_version: Int? = null
)

/**
 * Host operating system and runtime environment information.
 */
data class HostEnvironmentInfo(
    val os_name: String = "Unknown",
    val os_release: String = "",
    val os_version: String = "",
    val architecture: String = "",
    val lan_ip: String = "",
    val python: HostToolInfo = HostToolInfo(),
    val node: HostToolInfo = HostToolInfo(),
    val npm: HostToolInfo = HostToolInfo(),
    val git: HostToolInfo = HostToolInfo(),
    val venv_module_available: Boolean = false
)

/**
 * Diagnostic evaluation of a specific technology stack on the laptop.
 */
data class StackEvaluationItem(
    val tech_stack: String = "",
    val supported: Boolean = false,
    val missing_tools: List<String> = emptyList(),
    val incompatible_versions: List<String> = emptyList(),
    val remediation_steps: List<String> = emptyList(),
    val recommended_environment_type: String = ""
)

/**
 * Response from GET /api/environment/detect.
 */
data class EnvironmentDetectionResponse(
    val success: Boolean = true,
    val environment: HostEnvironmentInfo = HostEnvironmentInfo(),
    val stack_evaluations: Map<String, StackEvaluationItem> = emptyMap()
)

/**
 * Discrete setup operation in a SetupPlan.
 */
data class SetupOperationItem(
    val op_id: String = "",
    val op_type: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "pending",
    val command_args: List<String> = emptyList(),
    val affects_paths: List<String> = emptyList(),
    val estimated_duration_sec: Int = 0,
    val error: String? = null,
    val output_log: String = ""
)

/**
 * Response from POST /api/environment/plan and /api/environment/execute-plan.
 */
data class SetupPlanResponse(
    val success: Boolean = true,
    val plan_id: String = "",
    val project_id: String = "",
    val project_name: String = "",
    val tech_stack: String = "python",
    val workspace_path: String = "",
    val summary: String = "",
    val operations: List<SetupOperationItem> = emptyList(),
    val requires_approval: Boolean = true,
    val approved: Boolean = false,
    val status: String = "planned",
    val error: String? = null,
    val preview_url: String? = null,
    val preview_port: Int? = null
)

/**
 * Structured project blueprint from refined idea.
 */
data class IdeaBlueprintItem(
    val summary: String = "",
    val target_audience: String = "",
    val core_features: List<String> = emptyList(),
    val suggested_architecture: String = "",
    val recommended_tech_stack: String = "python",
    val key_dependencies: List<String> = emptyList()
)

/**
 * Permanent user idea record.
 */
data class IdeaItem(
    val idea_id: String = "",
    val title: String = "",
    val raw_prompt: String = "",
    val created_at: String = "",
    val updated_at: String = "",
    val status: String = "captured",
    val tech_stack: String = "auto",
    val blueprint: IdeaBlueprintItem? = null,
    val linked_project_id: String? = null
)

typealias IdeaRecordItem = IdeaItem

/**
 * Real-time setup execution status from GET /api/environment/status/{project_id}.
 */
data class SetupStatusResponse(
    val success: Boolean = true,
    val project_id: String = "",
    val status: String = "unknown",
    val progress_pct: Float = 0.0f,
    val current_operation: String? = null,
    val completed_operations: List<String> = emptyList(),
    val server_running: Boolean = false,
    val server_port: Int? = null,
    val preview_url: String? = null,
    val error: String? = null
)

