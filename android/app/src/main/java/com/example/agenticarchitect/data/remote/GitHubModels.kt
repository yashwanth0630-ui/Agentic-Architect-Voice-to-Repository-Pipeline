package com.example.agenticarchitect.data.remote

/**
 * Normalized GitHub User metadata model.
 * Never contains credentials or access tokens.
 */
data class GitHubUser(
    val login: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val htmlUrl: String? = null,
    val bio: String? = null,
    val publicRepos: Int = 0
)

/**
 * Status response from GET /github/status.
 */
data class GitHubStatusResponse(
    val success: Boolean,
    val authenticated: Boolean,
    val user: GitHubUser? = null,
    val message: String? = null
)

/**
 * Normalized repository metadata model.
 * Contains only public repository attributes suitable for display and selection.
 */
data class GitHubRepositoryItem(
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val htmlUrl: String,
    val cloneUrl: String,
    val description: String? = null,
    val updatedAt: String? = null,
    val language: String? = null,
    val stargazersCount: Int = 0,
    val fork: Boolean = false
)

/**
 * Response from GET /github/repos.
 */
data class GitHubReposResponse(
    val success: Boolean,
    val repositories: List<GitHubRepositoryItem> = emptyList(),
    val message: String? = null
)

/**
 * Response from GET /github/repos/{owner}/{repo}.
 */
data class GitHubRepoDetailResponse(
    val success: Boolean,
    val repository: GitHubRepositoryItem? = null,
    val message: String? = null
)

/**
 * Response from POST /github/auth/device/code.
 */
data class GitHubDeviceCodeResponse(
    val success: Boolean,
    val deviceCode: String? = null,
    val userCode: String? = null,
    val verificationUri: String? = null,
    val expiresIn: Int = 900,
    val interval: Int = 5,
    val message: String? = null
)

/**
 * Response from POST /github/auth/device/poll.
 */
data class GitHubDevicePollResponse(
    val success: Boolean,
    val status: String = "pending", // "pending", "complete", "expired", "denied", "slow_down"
    val authenticated: Boolean = false,
    val user: GitHubUser? = null,
    val interval: Int? = null,
    val message: String? = null
)

/**
 * Persistent selected target repository.
 */
data class SelectedRepository(
    val owner: String,
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val defaultBranch: String,
    val isPrivate: Boolean
)

/**
 * Phase 2C: Workspace metadata model.
 */
data class RepoWorkspaceRecord(
    val workspace_id: String,
    val owner: String,
    val repo: String,
    val clone_url: String,
    val default_branch: String,
    val workspace_path: String,
    val baseline_commit: String,
    val current_branch: String,
    val status: String = "ready",
    val preview_port: Int? = null,
    val preview_url: String? = null
)

data class RepoChangeSummary(
    val files_created: List<String> = emptyList(),
    val files_modified: List<String> = emptyList(),
    val files_deleted: List<String> = emptyList(),
    val total_changes: Int = 0,
    val diff: String = "",
    val baseline_commit: String = ""
)

data class RepoWorkspaceInitResponse(
    val success: Boolean,
    val workspace: RepoWorkspaceRecord? = null,
    val message: String? = null
)

data class RepoWorkspaceStatusResponse(
    val success: Boolean,
    val workspace: RepoWorkspaceRecord? = null,
    val git_status: Map<String, Any>? = null,
    val message: String? = null
)

data class RepoWorkspaceInstructResponse(
    val success: Boolean,
    val workspace_id: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val changes: RepoChangeSummary? = null,
    val preview_url: String? = null,
    val error: String? = null
)

data class RepoWorkspaceDiffResponse(
    val success: Boolean,
    val workspace_id: String? = null,
    val baseline_commit: String? = null,
    val changes: RepoChangeSummary? = null,
    val message: String? = null
)

data class RepoWorkspacePreviewResponse(
    val success: Boolean,
    val workspace_id: String? = null,
    val status: String? = null,
    val preview_url: String? = null,
    val port: Int? = null,
    val message: String? = null
)

/**
 * Phase 2D: Controlled Git Publishing Review & Execution Models
 */
data class FileReviewItem(
    val path: String,
    val action: String,
    val source: String,
    val eligible_for_commit: Boolean,
    val ineligibility_reason: String? = null,
    val staged_in_index: Boolean = false,
    val has_collision: Boolean = false
)

data class PublishReviewResponse(
    val success: Boolean = true,
    val workspace_id: String? = null,
    val owner: String? = null,
    val repo: String? = null,
    val current_branch: String? = null,
    val remote_name: String? = null,
    val remote_url: String? = null,
    val head_commit: String? = null,
    val baseline_commit: String? = null,
    val files: List<FileReviewItem> = emptyList(),
    val diff: String = "",
    val publish_blocked: Boolean = false,
    val publish_block_reason: String? = null,
    val operations_count: Int = 0,
    val latest_instruction: String? = null,
    val message: String? = null
) {
    val target_repo: String get() = if (owner != null && repo != null) "$owner/$repo" else (repo ?: "")
    val total_changed_files: Int get() = files.size
    val has_ai_changes: Boolean get() = files.any { it.source.contains("ai", ignoreCase = true) }
    val has_user_changes: Boolean get() = files.any { it.source.contains("user", ignoreCase = true) || it.source.contains("untracked", ignoreCase = true) }
    val has_preexisting_collisions: Boolean get() = files.any { it.has_collision || it.source.contains("coll", ignoreCase = true) }
    val block_reasons: List<String> get() = publish_block_reason?.split("|")?.map { it.trim() } ?: emptyList()
    val unpushed_commits: Int get() = 0
}

data class CommitRequest(
    val commit_message: String,
    val selected_files: List<String>,
    val author_name: String? = null,
    val author_email: String? = null
)

data class CommitResponse(
    val success: Boolean = true,
    val workspace_id: String? = null,
    val commit_hash: String? = null,
    val commit_message: String? = null,
    val committed_files: List<String> = emptyList(),
    val branch: String? = null,
    val timestamp: String? = null,
    val message: String? = null
) {
    val files_committed: List<String> get() = committed_files
}

data class PushRequest(
    val branch: String? = null,
    val remote_name: String? = "origin",
    val confirmed: Boolean = true
)

data class PushResponse(
    val success: Boolean = true,
    val workspace_id: String? = null,
    val pushed_commit: String? = null,
    val branch: String? = null,
    val remote: String? = null,
    val remote_url: String? = null,
    val timestamp: String? = null,
    val message: String? = null,
    val diverged: Boolean = false,
    val commits_ahead: Int = 0,
    val commits_behind: Int = 0,
    val recovery_steps: String? = null,
    val error: String? = null
)

