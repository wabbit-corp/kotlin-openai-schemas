# Module kotlin-openai-schemas

Kotlin Multiplatform helpers for deriving OpenAI function-tool schemas from `kotlinx.serialization`
descriptors.

Model tool requests as a sealed `@Serializable` hierarchy, annotate request classes and properties
with `FunctionSchema.Doc`, generate OpenAI tool declarations with `FunctionSchema.makeFunctions`,
and decode model tool calls with `FunctionSchema.parseFunctionCall`.

This module intentionally supports a small JSON Schema subset suitable for OpenAI function
parameters. It does not attempt to be a complete JSON Schema generator.
