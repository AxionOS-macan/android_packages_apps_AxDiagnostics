/*
 * Copyright 2025-2026 AxionOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.axion.diagnostics.data

import java.io.File

data class GpuSnapshot(
    val frequencyMhz: Int,
    val maxFrequencyMhz: Int,
    val minFrequencyMhz: Int,
    val busyPercent: Int,
    val governor: String,
    val availableFrequencies: List<Int>,
    val available: Boolean
)

object GpuCollector {

    fun collect(): GpuSnapshot {
        val config = KernelConfig.gpuConfig

        if (config.currentNode != null) {
            val freqFile = File(config.currentNode)
            if (freqFile.exists()) {
                val rawFreq = runCatching { freqFile.readText().trim().toLong() }.getOrDefault(0L)
                val curFreq = KernelConfig.scaleFreqToMhz(rawFreq)

                val busy = if (config.usageNode != null) {
                    val busyFile = File(config.usageNode)
                    if (busyFile.exists()) {
                        runCatching {
                            busyFile.readText().replace("%", "").trim().toInt()
                        }.getOrDefault(0).coerceIn(0, 100)
                    } else 0
                } else 0

                val governor = readGovernor(config)
                val maxFreq = readGpuFrequency(config.maxNode, config.node, "max_freq")
                val minFreq = readGpuFrequency(config.minNode, config.node, "min_freq")

                if (curFreq > 0) {
                    return GpuSnapshot(
                        frequencyMhz = curFreq,
                        maxFrequencyMhz = maxFreq,
                        minFrequencyMhz = minFreq,
                        busyPercent = busy,
                        governor = governor,
                        availableFrequencies = config.values,
                        available = true
                    )
                }
            }
        }

        return GpuSnapshot(0, 0, 0, 0, "unavailable", emptyList(), false)
    }

    private fun readGpuFrequency(path: String?, node: String?, fallbackName: String): Int {
        val file = path?.let { File(it) } ?: node?.let { File(it, fallbackName) } ?: return 0
        if (!file.exists()) return 0
        val raw = runCatching { file.readText().trim().toLong() }.getOrDefault(0L)
        return if (file.name.endsWith("_clock_mhz")) {
            raw.toInt()
        } else {
            KernelConfig.scaleFreqToMhz(raw)
        }
    }

    private fun readGovernor(config: GpuConfig): String {
        val file = config.governorNode?.let { File(it) }
            ?: config.node?.let { File(it, "governor") }
            ?: return "unknown"
        if (!file.exists()) return "unknown"
        val value = runCatching { file.readText().trim() }.getOrDefault("unknown")
        return when {
            file.name == "pwrscale" && value == "1" -> "pwrscale"
            file.name == "pwrscale" && value == "0" -> "fixed"
            value.isBlank() -> "unknown"
            else -> value
        }
    }
}
