package com.example.agent.execution

import com.example.agent.model.FunctionInfo
import com.example.agent.model.TestCase
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

/**
 * Outcome of actually invoking a compiled method via reflection with the
 * concrete arguments from a [TestCase].
 */
sealed class ExecutionOutcome {
    data class ReturnedValue(val value: Any?) : ExecutionOutcome()
    data class ThrewException(val exceptionClassName: String) : ExecutionOutcome()
    data class CouldNotExecute(val reason: String) : ExecutionOutcome()
}

/**
 * Executes a single [TestCase] against a real, in-memory-compiled class
 * (loaded via [InMemoryJavaCompiler]) through reflection, recording the
 * actual observed behavior (return value or thrown exception) as an
 * "oracle" that [com.example.agent.codegen.JUnit5TestCodeGenerator] can turn
 * into a real assertion.
 *
 * This class is deliberately defensive: it must never let a compilation or
 * reflection failure propagate out and break the enclosing HTTP request.
 * Every failure mode - unsupported parameter types, missing methods, missing
 * no-arg constructors, and even unexpected `Throwable`s from the invoked code
 * itself - is captured as [ExecutionOutcome.CouldNotExecute].
 */
class TestCaseExecutor {

    fun execute(
        classLoader: URLClassLoader,
        packageName: String,
        function: FunctionInfo,
        testCase: TestCase
    ): ExecutionOutcome {
        return try {
            executeInternal(classLoader, packageName, function, testCase)
        } catch (t: Throwable) {
            // Defensive catch-all: reflective invocation of arbitrary,
            // externally-supplied code can throw almost anything, including
            // Errors (e.g. NoClassDefFoundError). None of that should ever
            // bubble up and fail the /generate-tests request.
            ExecutionOutcome.CouldNotExecute("unexpected failure while executing test case: ${t.message}")
        }
    }

    private fun executeInternal(
        classLoader: URLClassLoader,
        packageName: String,
        function: FunctionInfo,
        testCase: TestCase
    ): ExecutionOutcome {
        val fqcn = if (packageName.isBlank()) function.className else "$packageName.${function.className}"

        val clazz = try {
            Class.forName(fqcn, false, classLoader)
        } catch (e: ClassNotFoundException) {
            return ExecutionOutcome.CouldNotExecute("class not found: $fqcn")
        }

        val parameterTypes = mutableListOf<Class<*>>()
        val arguments = mutableListOf<Any?>()

        for (parameter in function.parameters) {
            val reflectionType = TypeConversion.reflectionClassFor(parameter.type)
                ?: return ExecutionOutcome.CouldNotExecute("unsupported parameter type: ${parameter.type}")

            val rawValue = testCase.inputData[parameter.name]
            when (val converted = TypeConversion.convert(parameter.type, rawValue)) {
                is ConversionResult.Unsupported ->
                    return ExecutionOutcome.CouldNotExecute(converted.reason)
                is ConversionResult.Converted -> {
                    parameterTypes.add(reflectionType)
                    arguments.add(converted.value)
                }
            }
        }

        val method = try {
            clazz.getMethod(function.name, *parameterTypes.toTypedArray())
        } catch (e: NoSuchMethodException) {
            return ExecutionOutcome.CouldNotExecute("method not found: ${function.name}(${parameterTypes.joinToString()})")
        }

        val instance: Any? = if (function.isStatic) {
            null
        } else {
            try {
                val constructor = clazz.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance()
            } catch (e: NoSuchMethodException) {
                return ExecutionOutcome.CouldNotExecute("no no-arg constructor available for $fqcn")
            } catch (e: ReflectiveOperationException) {
                return ExecutionOutcome.CouldNotExecute("could not instantiate $fqcn: ${e.message}")
            }
        }

        return try {
            val result = method.invoke(instance, *arguments.toTypedArray())
            ExecutionOutcome.ReturnedValue(result)
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            ExecutionOutcome.ThrewException(cause?.javaClass?.name ?: "java.lang.Throwable")
        } catch (e: IllegalAccessException) {
            ExecutionOutcome.CouldNotExecute("method not accessible: ${e.message}")
        }
    }
}
