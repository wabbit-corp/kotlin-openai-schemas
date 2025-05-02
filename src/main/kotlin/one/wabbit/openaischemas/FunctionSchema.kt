package one.wabbit.openaischemas

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.core.Parameters
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.json.*
import one.wabbit.data.Ref
import kotlin.String

import kotlin.String as KString
import kotlin.collections.List as KList

object FunctionSchema {
    @OptIn(ExperimentalSerializationApi::class)
    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
    @SerialInfo
    annotation class Doc(val value: KString)

    data class ToolDef(
        val annotations: KList<Annotation>,
        val compiledToolSchema: Tool
    )

    @Serializable data class FunctionDef(
        val name: KString,
        val argType: TypeDef,
        val description: KString?,
    )

    @Serializable enum class PrimType {
        CHAR,
        STRING,
        BYTE,
        SHORT,
        INT,
        LONG,
        BOOL,
        FLOAT,
        DOUBLE,
    }

    @Serializable
    sealed interface TypeDef {
        @Serializable data class Prim(val type: PrimType): TypeDef
        @Serializable data class Literal(val value: KString): TypeDef

        @Serializable data class Enum(
            val name: KString,
            val values: KList<EnumValue>,
            val description: KString?
        ) : TypeDef
        @Serializable data class EnumValue(
            val name: KString,
            val description: KString?
        )

        @Serializable data class Array(val elementType: TypeDef) : TypeDef
        @Serializable data class Map(val keyType: TypeDef, val valueType: TypeDef) : TypeDef
        @Serializable data class Alias(val name: KString, val type: TypeDef, val description: KString?) : TypeDef

        @Serializable data class Object(
            val name: KString,
            val fields: KList<Field>,
            val description: KString?
        ) : TypeDef
        @Serializable data class Field(
            val name: KString,
            val type: TypeDef,
            val description: KString?
        )

        @Serializable data class Sealed(
            val name: KString,
            val subtypes: KList<KString>,
            val description: KString?
        ) : TypeDef

        @Serializable data class Nullable(val type: TypeDef) : TypeDef

        /*
        {
                        "type": "object",
                        "properties": {
                            "userId": {
                                "type": ["string", "null", "integer"],
                                "description": "The ID of the user."
                            },
                            "userName": {
                                "type": ["string", "null"],
                                "description": "The name of the user."
                            },
                            "includeUserAvatar": {
                                "type": "boolean",
                                "description": "Include the user's avatar?"
                            }
                        }
                    }

        {
                        "type": "object",
                        "properties": {
                            "template_name": {
                                "type": "string",
                                "description": "The name of the meme template on Imgflip."
                            },
                            "box_text": {
                                "type": "array",
                                "description": "The text to put in each box of the meme.",
                                "items": {
                                    "type": "string"
                                }
                            }
                        },
                        "required": ["template_name", "box_text"]
                    }

                    {
                        "type": "object",
                        "properties": {
                            "title": {
                                "type": "string",
                                "description": "The title of the memory."
                            },
                            "content": {
                                "type": ["string", "null"],
                                "description": "The content of the memory. Provide enough context to the memory so that you can always understand why you made it."
                            },
                            "important": {
                                "type": ["boolean", "null"],
                                "description": "Whether the memory is important."
                            }
                        },
                        "required": ["title"]
                    }
         */
        fun toJsonSchema(): JsonElement = when (this) {
            is Prim -> {
                val type = when (type) {
                    PrimType.CHAR -> "string"
                    PrimType.STRING -> "string"
                    PrimType.BYTE -> "number"
                    PrimType.SHORT -> "number"
                    PrimType.INT -> "number"
                    PrimType.LONG -> "number"
                    PrimType.BOOL -> "boolean"
                    PrimType.FLOAT -> "number"
                    PrimType.DOUBLE -> "number"
                }
                JsonObject(mapOf("type" to JsonPrimitive(type)))
            }
            is Alias -> {
                type.toJsonSchema()
            }
            is Array -> {
                JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to elementType.toJsonSchema()
                ))
            }
            is Enum -> {
                JsonObject(mapOf(
                    "type" to JsonPrimitive("string"),
                    "enum" to JsonArray(values.map { JsonPrimitive(it.name) })
                ))
            }
            is Literal -> {
                JsonObject(mapOf(
                    "type" to JsonPrimitive("string"),
                    "enum" to JsonArray(listOf(JsonPrimitive(value)))
                ))
            }
            is Map -> {
                JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "additionalProperties" to valueType.toJsonSchema()
                ))
            }
            is Nullable -> type.toJsonSchema()
//                JsonObject(mapOf(
//                "type" to JsonArray(listOf(JsonPrimitive("null"), simplifySchema(type.toJsonSchema())))
//            ))
            is Object -> {
                val result = mutableMapOf<KString, JsonElement>()
                for (field in fields) {
                    val fieldType = addDescription(field.type.toJsonSchema() as JsonObject, field.description)
                    result[field.name] = fieldType
                }
                JsonObject(mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(result),
                    "required" to JsonArray(fields.filter { it.type !is Nullable }.map { JsonPrimitive(it.name) })
                ))
            }
            is Sealed -> TODO()
        }

        fun addDescription(json: JsonElement, description: String?): JsonObject {
            if (json !is JsonObject) {
                check(json is JsonArray || json is JsonPrimitive) // unions + primitives
                val newFields = mutableMapOf<KString, JsonElement>()
                if (description != null) {
                    check("description" !in newFields)
                    newFields["description"] = JsonPrimitive(description)
                }
                newFields["type"] = json
                return JsonObject(newFields)
            }

            val newFields = json.toMutableMap()
            if (description != null) {
                check("description" !in newFields)
                newFields["description"] = JsonPrimitive(description)
            }

            return JsonObject(newFields)
        }

//        fun simplifySchema(json: JsonElement): JsonElement {
//            if (json is JsonObject && json.keys == setOf("type")) {
//                return json.values.first()
//            }
//            return json
//        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun fromClassDescriptor(descriptor: SerialDescriptor, cache: MutableMap<Ref<SerialDescriptor>, TypeDef>, typeName: KString? = null): TypeDef.Object {
        if (Ref(descriptor) in cache) {
            return cache.getValue(Ref(descriptor)) as TypeDef.Object
        }

        val name = descriptor.serialName.split(".").last()
        val docString = descriptor.annotations.filterIsInstance<Doc>().firstOrNull()?.value

        check(descriptor.kind == StructureKind.OBJECT || descriptor.kind == StructureKind.CLASS)

        if (descriptor.kind == StructureKind.OBJECT) {
            return TypeDef.Object(name, emptyList(), docString)
        }

        val fields = mutableListOf<TypeDef.Field>()
        if (typeName != null) {
            fields.add(TypeDef.Field("type", TypeDef.Literal(typeName), null))
        }

        for (i in 0 until descriptor.elementsCount) {
            val fieldDescriptor = descriptor.getElementDescriptor(i)
            val fieldName = descriptor.getElementName(i)
            val fieldDocString = descriptor.getElementAnnotations(i).filterIsInstance<Doc>().firstOrNull()?.value
            val type = def(fieldDescriptor, cache)
            fields.add(TypeDef.Field(fieldName, type, fieldDocString))
        }

        val result = TypeDef.Object(name, fields, docString)
        cache[Ref(descriptor)] = result
        return result
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun def(descriptor: SerialDescriptor, cache: MutableMap<Ref<SerialDescriptor>, TypeDef>): TypeDef {
        if (Ref(descriptor) in cache) {
            return cache.getValue(Ref(descriptor))
        }

        val name = descriptor.serialName.split(".").last()
        val docString = descriptor.annotations.filterIsInstance<Doc>().firstOrNull()?.value

        val result: TypeDef = when (descriptor.kind) {
            is PrimitiveKind.CHAR    -> TypeDef.Prim(PrimType.CHAR)
            is PrimitiveKind.STRING  -> TypeDef.Prim(PrimType.STRING)
            is PrimitiveKind.BYTE    -> TypeDef.Prim(PrimType.BYTE)
            is PrimitiveKind.SHORT   -> TypeDef.Prim(PrimType.SHORT)
            is PrimitiveKind.INT     -> TypeDef.Prim(PrimType.INT)
            is PrimitiveKind.LONG    -> TypeDef.Prim(PrimType.LONG)
            is PrimitiveKind.FLOAT   -> TypeDef.Prim(PrimType.FLOAT)
            is PrimitiveKind.DOUBLE  -> TypeDef.Prim(PrimType.DOUBLE)
            is PrimitiveKind.BOOLEAN -> TypeDef.Prim(PrimType.BOOL)

            is StructureKind.LIST -> {
                val e = descriptor.getElementDescriptor(0)
                val type = def(e, cache)
                TypeDef.Array(type)
            }
            is StructureKind.MAP -> {
                val key = descriptor.getElementDescriptor(0)
                val value = descriptor.getElementDescriptor(1)
                val keyType = def(key, cache)
                val valueType = def(value, cache)
                TypeDef.Map(keyType, valueType)
            }

            is SerialKind.ENUM -> {
                // type Foo = "a" | "b"
                TypeDef.Enum(name, (0 until descriptor.elementsCount).map {
                    val valueName = descriptor.getElementName(it)
                    val valueDocString =
                        descriptor.getElementAnnotations(it).filterIsInstance<Doc>().firstOrNull()?.value
                    TypeDef.EnumValue(valueName, valueDocString)
                }, docString)
            }

            is StructureKind.CLASS ->
                if (!descriptor.isInline) fromClassDescriptor(descriptor, cache)
                else {
                    check(descriptor.elementsCount == 1)
                    val elemDesc = descriptor.getElementDescriptor(0)
                    val type = def(elemDesc, cache)
                    TypeDef.Alias(name, type, docString)
                }
            StructureKind.OBJECT -> fromClassDescriptor(descriptor, cache)

            PolymorphicKind.SEALED -> {
                check(descriptor.getElementName(0) == "type")
                check(descriptor.getElementName(1) == "value")
                val valueType = descriptor.getElementDescriptor(1)
                check(valueType.kind == SerialKind.CONTEXTUAL)

                val subtypes = mutableListOf<KString>()
                for (i in 0 until valueType.elementsCount) {
                    val serialName = valueType.getElementName(i)
                    val name = serialName.split(".").last()
                    val elemDesc = valueType.getElementDescriptor(i)
                    check(elemDesc.kind == StructureKind.CLASS || elemDesc.kind == StructureKind.OBJECT)
                    val tpe = fromClassDescriptor(elemDesc, cache, name)
                    subtypes.add(name)
                }

                TypeDef.Sealed(name, subtypes, docString)
            }

            is SerialKind.CONTEXTUAL -> TODO()
            PolymorphicKind.OPEN -> TODO()
        }

        if (descriptor.isNullable) {
            return TypeDef.Nullable(result)
        }

        // Don't store built-in types in the cache
        cache[Ref(descriptor)] = result
        return result
    }

    inline fun <reified T> def(cache: MutableMap<Ref<SerialDescriptor>, TypeDef> = mutableMapOf()): TypeDef {
        return def(serializer<T>().descriptor, cache)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun makeFunctions(descriptor: SerialDescriptor): KList<ToolDef> {
        require(!descriptor.isInline) { "Top-level descriptor $descriptor must not be inline" }
        require(!descriptor.isNullable) { "Top-level descriptor $descriptor must not be nullable" }
        require(descriptor.kind == PolymorphicKind.SEALED) { "Top-level descriptor $descriptor must be sealed" }
        require(descriptor.getElementName(0) == "type") { "First element of $descriptor must be 'type'" }
        require(descriptor.getElementName(1) == "value") { "Second element of $descriptor must be 'value'" }
        val valueType = descriptor.getElementDescriptor(1)
        require(valueType.kind == SerialKind.CONTEXTUAL) { "Second element of $descriptor must be contextual" }

        val cache = mutableMapOf<Ref<SerialDescriptor>, TypeDef>()
        val result = mutableListOf<ToolDef>()

        for (i in 0 until valueType.elementsCount) {
            val name = valueType.getElementName(i)
            val elemDesc = valueType.getElementDescriptor(i)
            val description = elemDesc.annotations.filterIsInstance<Doc>().firstOrNull()?.value
            val annotations = elemDesc.annotations

            require('.' !in name) { "Invalid name: $name in $descriptor" }
            require(elemDesc.kind == StructureKind.CLASS || elemDesc.kind == StructureKind.OBJECT)

            val tpe = fromClassDescriptor(
                elemDesc,
                cache,
                // NOTE: We explicitly set the typeName to null since we will be getting the type separately.
                typeName = null)

            if (tpe.fields.isEmpty()) {
                result.add(ToolDef(
                    compiledToolSchema = Tool(
                        type = ToolType.Function,
                        function = FunctionTool(
                            name = name,
                            description = description
                        )
                    ),
                    annotations = annotations
                ))
                continue
            }

            result.add(ToolDef(
                compiledToolSchema = Tool(
                    type = ToolType.Function,
                    function = FunctionTool(
                        name = name,
                        parameters = Parameters(tpe.toJsonSchema() as JsonObject),
                        description = description
                    )
                ),
                annotations = annotations
            ))
        }

        return result
    }

    fun <Request> parseFunctionCall(serializer: KSerializer<Request>, name: String, arguments: JsonObject): Request {
        val fields = mutableMapOf<KString, JsonElement>("type" to JsonPrimitive(name))
        for ((k, v) in arguments) {
            fields[k] = v
        }
        return Json.decodeFromJsonElement(serializer, JsonObject(fields))
    }

    fun <Request> parseFunctionCall(serializer: KSerializer<Request>, name: String, arguments: String): Request =
        parseFunctionCall(serializer, name, Json.decodeFromString<JsonObject>(arguments))

    fun <Request> parseFunctionCall(serializer: KSerializer<Request>, req: FunctionCall): Request =
        parseFunctionCall(serializer, req.name, req.arguments)

    inline fun <reified Request> parseFunctionCall(req: FunctionCall): Request =
        parseFunctionCall(serializer(), req)
}
