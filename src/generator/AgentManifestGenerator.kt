package com.iqoo.agent.generator

import java.io.File

/**
 * Data model for project configuration used across AI agent manifests.
 */
data class AgentProjectConfig(
    val projectName: String,
    val description: String,
    val runtime: String = "Node.js 24.x / TypeScript 5.x",
    val architecture: String = "Modular Monolith",
    val installCommand: String = "npm install",
    val devCommand: String = "npm run dev",
    val lintCommand: String = "npm run lint",
    val testCommand: String = "npm test",
    val frontendFramework: String = "React 18+ / Next.js 14+ App Router",
    val backendFramework: String = "Express / Node.js 24+",
    val stylingEngine: String = "Tailwind CSS",
    val dataLayer: String = "Zod, Prisma, TanStack Query"
)

/**
 * High-performance string-template generator for dynamically generating
 * AI agent manifest files on Android (phone) or JVM environments.
 */
class AgentManifestGenerator {

    fun generateAgentsMarkdown(config: AgentProjectConfig): String {
        return """
        # Agent Context & Architectural Guidelines
        
        ## 1. Project Overview
        - **Project Name:** ${config.projectName}
        - **Description:** ${config.description}
        - **Primary Runtime:** ${config.runtime}
        - **Primary Architecture:** ${config.architecture}
        
        ## 2. Directory Layout & Module Ownership
        ```text
        ├── src/
        │   ├── api/          # Route handlers and external adapters
        │   ├── core/         # Pure business logic and domain entities
        │   ├── components/   # Presentation layer & reusable UI primitives
        │   └── lib/          # Utilities, clients, and database singletons
        ├── tests/            # Integration and end-to-end test suites
        └── skills/           # Specific architectural domain guidelines
        ```
        
        ## 3. Standard Commands
        Always execute these exact commands; do not guess alternate scripts.
        
        - **Install Dependencies:** ${config.installCommand}
        - **Run Local Server:** ${config.devCommand}
        - **Lint & Format:** ${config.lintCommand}
        - **Execute Test Suite:** ${config.testCommand}
        
        ## 4. Operational Boundaries & Safety Guardrails
        - **File Modifications:** Never edit auto-generated files (e.g., dist/, .next/, lockfiles) directly.
        - **Sensitive Data:** Never hardcode secrets, API keys, or private tokens. Always read from environment variables defined in .env.example.
        - **Destructive Commands:** Never execute destructive Git commands (git reset --hard, git push --force) or recursive deletions (rm -rf /) without explicit confirmation.
        - **Scope Creep:** Implement only what was requested. Avoid rewriting unrelated modules or changing existing formatting styles unnecessarily.
        
        ## 5. Domain Skill Index
        Consult the dedicated documents in skills/ before authoring logic for these subsystems:
        
        - **UI & Styling:** skills/ui-component-system.md
        - **Backend & Contracts:** skills/api-contracts.md
        - **Testing Expectations:** skills/testing-patterns.md
        """.trimIndent()
    }

    fun generateCursorRules(config: AgentProjectConfig): String {
        return """
        You are an expert full-stack engineer building ${config.projectName}. Follow these instructions strictly across all code generations.
        
        ### Core Tech Stack
        - Frontend: ${config.frontendFramework}
        - Backend: ${config.backendFramework}
        - Styling: ${config.stylingEngine}
        - State / Data Fetching: ${config.dataLayer}
        
        ### Code Style & Implementation Rules
        1. **Type Safety:** 
           - Write strict, fully typed code. Avoid `any` in TypeScript or unannotated signatures in Python.
           - Use runtime validation (e.g., Zod or Pydantic) at all system boundaries (APIs, forms, local storage).
        
        2. **Component Architecture:**
           - Prefer functional components with clear prop interfaces.
           - Separate stateful logic into custom hooks; keep UI rendering components pure and declarative.
           - Ensure interactive UI elements have accessible labels (`aria-label`, correct semantic HTML tags).
        
        3. **Error Handling & Logging:**
           - Avoid empty `catch` blocks. Always handle errors gracefully with typed error wrappers.
           - Log actionable error messages that include contextual identifiers, avoiding raw object dumps.
        
        4. **Testing Expectations:**
           - When generating new features, write corresponding unit tests covering both the happy path and critical edge cases.
           - Mock external network requests and persistent database instances during test runs.
        
        ### Anti-Patterns (Do NOT Do)
        - Do not import client-only packages inside server components/modules.
        - Do not add new external npm/pip dependencies without explicitly notifying the developer.
        - Do not use inline styles when utility classes (e.g., Tailwind) are configured.
        """.trimIndent()
    }

    fun generateApiContracts(): String {
        return """
        # Skill: API Design & Error Contracts
        
        ## Response Schema Format
        All JSON responses from internal endpoints must conform to this schema:
        
        ### Success Response
        ```json
        {
          "success": true,
          "data": {},
          "meta": {
            "timestamp": "ISO-8601 string",
            "requestId": "uuid-v4"
          }
        }
        ```
        
        ### Error Response (RFC 7807 Pattern)
        ```json
        {
          "success": false,
          "error": {
            "code": "RESOURCE_NOT_FOUND",
            "message": "Human-readable explanation",
            "details": []
          }
        }
        ```
        
        ## Authentication & Headers
        Client calls must supply bearer authorization: Authorization: Bearer <token>.
        
        Internal service-to-service calls require the X-Internal-Secret header.
        """.trimIndent()
    }

    fun writeFilesToDirectory(targetDir: File, config: AgentProjectConfig) {
        targetDir.mkdirs()
        File(targetDir, "AGENTS.md").writeText(generateAgentsMarkdown(config))
        File(targetDir, ".cursorrules").writeText(generateCursorRules(config))
        
        val skillsDir = File(targetDir, "skills").apply { mkdirs() }
        File(skillsDir, "api-contracts.md").writeText(generateApiContracts())
    }
}
