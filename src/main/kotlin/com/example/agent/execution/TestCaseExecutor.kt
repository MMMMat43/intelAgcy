package com.example.agent.execution

import com.example.agent.model.FunctionInfo
import com.example.agent.model.FunctionKind
import com.example.agent.model.TestCase
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicReference

sealed class ExecutionOutcome {
    data class ReturnedValue(val value: Any?) : ExecutionOutcome()
    data class ThrewException(val exceptionClassName: String) : ExecutionOutcome()
    data class CouldNotExecute(val reason: String) : ExecutionOutcome()
}

class TestCaseExecutor(private val timeoutMillis: Long = 10_000) {

    fun execute(classLoader: ClassLoader, function: FunctionInfo, testCase: TestCase): ExecutionOutcome {
        val result = AtomicReference<ExecutionOutcome?>(null)
        val worker = Thread {
            val outcome = try {
                executeInternal(classLoader, function, testCase)
            } catch (t: Throwable) {
                ExecutionOutcome.CouldNotExecute("unexpected failure while executing test case: ${t.message}")
            }
            result.set(outcome)
        }
        worker.isDaemon = true
        worker.start()
        worker.join(timeoutMillis)
        return result.get() ?: ExecutionOutcome.CouldNotExecute("execution timed out after $timeoutMillis ms")
    }

    private fun executeInternal(classLoader: ClassLoader, function: FunctionInfo, testCase: TestCase): ExecutionOutcome {
        val fqcn = if (function.packageName.isBlank()) function.className else "${function.packageName}.${function.className}"

        val clazz = try {
            Class.forName(fqcn, false, classLoader)
        } catch (e: ClassNotFoundException) {
            return ExecutionOutcome.CouldNotExecute("class not found: $fqcn")
        }

        val parameterTypes = mutableListOf<Class<*>>()
        val arguments = mutableListOf<Any?>()

        for (parameter in function.parameters) {
            val reflectionType = TypeConversion.reflectionClassFor(parameter.type, parameter.nullable)
                ?: return ExecutionOutcome.CouldNotExecute("unsupported parameter type: ${parameter.type}")

            when (val converted = TypeConversion.convert(parameter.type, parameter.nullable, testCase.inputData[parameter.name])) {
                is ConversionResult.Unsupported -> return ExecutionOutcome.CouldNotExecute(converted.reason)
                is ConversionResult.Converted -> {
                    parameterTypes.add(reflectionType)
                    arguments.add(converted.value)
                }
            }
        }

        val target: Any?
        val owner: Class<*>
        try {
            when (function.kind) {
                FunctionKind.TOP_LEVEL -> {
                    target = null
                    owner = clazz
                }
                FunctionKind.OBJECT_MEMBER -> {
                    target = clazz.getField("INSTANCE").get(null)
                    owner = clazz
                }
                FunctionKind.COMPANION_MEMBER -> {
                    target = clazz.getField("Companion").get(null)
                    owner = target.javaClass
                }
                else -> {
                    val constructor = clazz.getDeclaredConstructor()
                    constructor.isAccessible = true
                    target = constructor.newInstance()
                    owner = clazz
                }
            }
        } catch (e: NoSuchMethodException) {
            return ExecutionOutcome.CouldNotExecute("no no-arg constructor available for $fqcn")
        } catch (e: NoSuchFieldException) {
            return ExecutionOutcome.CouldNotExecute("singleton field not found for $fqcn: ${e.message}")
        } catch (e: ReflectiveOperationException) {
            return ExecutionOutcome.CouldNotExecute("could not instantiate $fqcn: ${e.message}")
        }

        val method: Method = try {
            owner.getMethod(function.name, *parameterTypes.toTypedArray())
        } catch (e: NoSuchMethodException) {
            return ExecutionOutcome.CouldNotExecute("method not found: ${function.name}(${parameterTypes.joinToString { it.simpleName }})")
        }
        runCatching { method.isAccessible = true }

        return try {
            ExecutionOutcome.ReturnedValue(method.invoke(target, *arguments.toTypedArray()))
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            when (cause) {
                null -> ExecutionOutcome.CouldNotExecute("invocation failed without a cause")
                is Error -> ExecutionOutcome.CouldNotExecute("invocation raised ${cause.javaClass.name}")
                else -> ExecutionOutcome.ThrewException(cause.javaClass.name)
            }
        } catch (e: IllegalAccessException) {
            ExecutionOutcome.CouldNotExecute("method not accessible: ${e.message}")
        }
    }
}
