package com.example.agent.coverage

import com.example.agent.model.FunctionInfo

interface BranchValueProvider {
    fun propose(function: FunctionInfo, uncoveredBranches: List<String>): List<Map<String, String?>>
}
