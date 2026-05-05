# User Guide

## Model Tool Requests

Define one sealed request hierarchy and one subtype per tool:

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import one.wabbit.openaischemas.FunctionSchema.Doc

@Serializable
sealed interface ToolRequest {
    @Doc("Generate a meme given a template and text.")
    @SerialName("GenerateMeme")
    @Serializable
    data class GenerateMeme(
        @Doc("The name of the meme template.")
        val templateName: String,
        @Doc("The text to put in each meme box.")
        val boxText: List<String>,
    ) : ToolRequest

    @Doc("List all available meme templates.")
    @SerialName("ListAllMemes")
    @Serializable
    data object ListAllMemes : ToolRequest
}
```

Use `@SerialName` values without dots. The generated OpenAI function name is the subtype serial
name, and `makeFunctions` rejects dotted names.

## Generate OpenAI Tools

```kotlin
import one.wabbit.openaischemas.FunctionSchema

val tools = FunctionSchema.makeFunctions(ToolRequest.serializer().descriptor)

check(tools.map { it.compiledToolSchema.function.name } == listOf("GenerateMeme", "ListAllMemes"))
```

Subtypes with constructor fields receive a `parameters` schema. Object subtypes with no fields are
generated as parameterless tools.

## Decode Function Calls

Use the same sealed request serializer to decode the model's selected function and arguments:

```kotlin
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import one.wabbit.openaischemas.FunctionSchema

val request = FunctionSchema.parseFunctionCall(
    serializer = ToolRequest.serializer(),
    name = "GenerateMeme",
    arguments = JsonObject(
        mapOf(
            "templateName" to JsonPrimitive("classic"),
            "boxText" to JsonArray(listOf(JsonPrimitive("top"), JsonPrimitive("bottom"))),
        )
    ),
)

check(request is ToolRequest.GenerateMeme)
```

There are overloads for parsed `JsonObject`, raw JSON object strings, and the OpenAI SDK
`FunctionCall` type.

## Inspect Type Definitions

Use `FunctionSchema.def<T>()` when you need the intermediate schema tree instead of OpenAI SDK tool
objects:

```kotlin
val typeDef = FunctionSchema.def<ToolRequest.GenerateMeme>()
val jsonSchema = typeDef.toJsonSchema()
```

`TypeDef` is useful for diagnostics and custom emitters, but it is not a complete JSON Schema AST.

## Nullable Fields

Nullable object fields are treated as optional for `required` generation:

```kotlin
@Serializable
data class SaveNote(
    val title: String,
    val body: String?,
)
```

The generated object schema requires `title` but not `body`. The current JSON Schema lowering emits
the underlying `body` type and does not add an explicit `"null"` union.
