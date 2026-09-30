package com.example.agent.assembly

import com.example.agent.api.PipelineListener
import com.example.agent.api.PipelineService
import com.example.agent.coverage.BranchValueProvider
import com.example.agent.generation.LlmUncoveredBranchSuggester
import com.example.agent.llm.LlmConfig
import com.example.agent.llm.OpenAiCompatibleLlmClient
import com.example.agent.source.SourceLoaderFactory

object AgentAssembly {

    fun valueProvider(): BranchValueProvider =
        LlmUncoveredBranchSuggester(OpenAiCompatibleLlmClient(LlmConfig.fromEnv()))

    fun pipelineService(
        listeners: List<PipelineListener> = emptyList(),
        sourceLoaderFactory: SourceLoaderFactory = SourceLoaderFactory()
    ): PipelineService = PipelineService(
        listeners = listeners,
        sourceLoaderFactory = sourceLoaderFactory,
        valueProviderFactory = { valueProvider() }
    )
}
