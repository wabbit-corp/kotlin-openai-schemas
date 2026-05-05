// SPDX-License-Identifier: AGPL-3.0-or-later

package one.wabbit.openaischemas

import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.FunctionTool
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolType
import com.aallam.openai.api.core.Parameters
import kotlin.String as KString
import kotlin.String
import kotlin.collections.List as KList
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer
import one.wabbit.data.Ref

/**
 * Utilities for deriving OpenAI function-tool schemas from `kotlinx.serialization` descriptors.
 *
 * The main workflow is to model tool requests as a sealed `@Serializable` hierarchy, annotate
 * classes and properties with [Doc] for descriptions, call [makeFunctions] to create OpenAI tool
 * declarations, and call [parseFunctionCall] to decode model tool calls back into the sealed
 * request type.
 */
object FunctionSchema {
    /**
     * Adds human-readable documentation to generated function tools and JSON-schema fields.
     *
     * Apply this annotation to serializable request classes, enum entries, and properties. The
     * values are read from serialization descriptors, so the annotation participates in schema
     * generation without requiring reflection.
     *
     * @property value the description text to attach to the generated schema element.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
    @SerialInfo
    annotation class Doc(val value: KString)

    /**
     * A generated OpenAI tool plus the annotations found on the source request subtype.
     *
     * @property annotations annotations attached to the serializable request subtype.
     * @property compiledToolSchema the OpenAI [Tool] declaration that can be sent to the API.
     */
    data class ToolDef(val annotations: KList<Annotation>, val compiledToolSchema: Tool)

    /**
     * Serializable intermediate description of a function-like request.
     *
     * This type is useful when callers want to inspect or persist a function definition before
     * lowering it to the OpenAI SDK [Tool] type.
     *
     * @property name the generated function name.
     * @property argType the argument schema expressed as a [TypeDef].
     * @property description optional human-readable function description.
     */
    @Serializable
    data class FunctionDef(val name: KString, val argType: TypeDef, val description: KString?)

    /** Primitive Kotlin serialization kinds understood by this schema model. */
    @Serializable
    enum class PrimType {
        /** Kotlin `Char`, represented as a JSON string. */
        CHAR,
        /** Kotlin `String`, represented as a JSON string. */
        STRING,
        /** Kotlin `Byte`, represented as a JSON number. */
        BYTE,
        /** Kotlin `Short`, represented as a JSON number. */
        SHORT,
        /** Kotlin `Int`, represented as a JSON number. */
        INT,
        /** Kotlin `Long`, represented as a JSON number. */
        LONG,
        /** Kotlin `Boolean`, represented as a JSON boolean. */
        BOOL,
        /** Kotlin `Float`, represented as a JSON number. */
        FLOAT,
        /** Kotlin `Double`, represented as a JSON number. */
        DOUBLE,
    }

    /**
     * Internal schema tree derived from Kotlin serialization descriptors.
     *
     * `TypeDef` captures enough structure to build OpenAI function `parameters` schemas. It is not
     * intended to be a complete JSON Schema AST.
     */
    @Serializable
    sealed interface TypeDef {
        /**
         * A primitive Kotlin serialization type.
         *
         * @property type the primitive kind.
         */
        @Serializable data class Prim(val type: PrimType) : TypeDef

        /**
         * A string literal represented as a single-value enum in JSON Schema.
         *
         * @property value the only accepted string value.
         */
        @Serializable data class Literal(val value: KString) : TypeDef

        /**
         * A serializable enum type.
         *
         * @property name the enum type name.
         * @property values enum entries and their optional descriptions.
         * @property description optional type-level description from [Doc].
         */
        @Serializable
        data class Enum(
            val name: KString,
            val values: KList<EnumValue>,
            val description: KString?,
        ) : TypeDef

        /**
         * A serializable enum entry.
         *
         * @property name the serialized enum entry name.
         * @property description optional entry description from [Doc].
         */
        @Serializable data class EnumValue(val name: KString, val description: KString?)

        /**
         * A list or array type.
         *
         * @property elementType schema for each element.
         */
        @Serializable data class Array(val elementType: TypeDef) : TypeDef

        /**
         * A map type.
         *
         * JSON Schema output uses [valueType] as `additionalProperties`; [keyType] is retained in
         * the model but JSON object keys are still strings in the emitted schema.
         *
         * @property keyType schema for source map keys.
         * @property valueType schema for source map values.
         */
        @Serializable data class Map(val keyType: TypeDef, val valueType: TypeDef) : TypeDef

        /**
         * An inline/value-class alias.
         *
         * @property name the alias type name.
         * @property type the underlying value type.
         * @property description optional type-level description from [Doc].
         */
        @Serializable
        data class Alias(val name: KString, val type: TypeDef, val description: KString?) : TypeDef

        /**
         * A serializable object or class type.
         *
         * @property name the object or class type name.
         * @property fields serializable properties included in the schema.
         * @property description optional type-level description from [Doc].
         */
        @Serializable
        data class Object(val name: KString, val fields: KList<Field>, val description: KString?) :
            TypeDef

        /**
         * A serializable class property.
         *
         * @property name the serialized field name.
         * @property type schema for the field value.
         * @property description optional field description from [Doc].
         */
        @Serializable
        data class Field(val name: KString, val type: TypeDef, val description: KString?)

        /**
         * A sealed hierarchy.
         *
         * This representation is produced by [def], but [toJsonSchema] does not currently lower it
         * directly. [makeFunctions] instead turns each sealed subtype into a separate OpenAI tool.
         *
         * @property name the sealed type name.
         * @property subtypes serialized subtype names.
         * @property description optional type-level description from [Doc].
         */
        @Serializable
        data class Sealed(
            val name: KString,
            val subtypes: KList<KString>,
            val description: KString?,
        ) : TypeDef

        /**
         * A nullable type.
         *
         * Object fields with this wrapper are omitted from the generated `required` list. The
         * current JSON Schema lowering emits the underlying schema and does not add an explicit
         * `"null"` union.
         *
         * @property type the non-null schema.
         */
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
        /**
         * Converts this schema node to the JSON Schema subset used for OpenAI function parameters.
         *
         * Unsupported sealed and open-polymorphic shapes throw at generation time rather than
         * emitting misleading schemas.
         *
         * @return a JSON schema fragment for this type.
         */
        fun toJsonSchema(): JsonElement =
            when (this) {
                is Prim -> {
                    val type =
                        when (type) {
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
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("array"),
                            "items" to elementType.toJsonSchema(),
                        )
                    )
                }
                is Enum -> {
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "enum" to JsonArray(values.map { JsonPrimitive(it.name) }),
                        )
                    )
                }
                is Literal -> {
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("string"),
                            "enum" to JsonArray(listOf(JsonPrimitive(value))),
                        )
                    )
                }
                is Map -> {
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("object"),
                            "additionalProperties" to valueType.toJsonSchema(),
                        )
                    )
                }
                is Nullable -> type.toJsonSchema()
                //                JsonObject(mapOf(
                //                "type" to JsonArray(listOf(JsonPrimitive("null"),
                // simplifySchema(type.toJsonSchema())))
                //            ))
                is Object -> {
                    val result = mutableMapOf<KString, JsonElement>()
                    for (field in fields) {
                        val fieldType =
                            addDescription(
                                field.type.toJsonSchema() as JsonObject,
                                field.description,
                            )
                        result[field.name] = fieldType
                    }
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("object"),
                            "properties" to JsonObject(result),
                            "required" to
                                JsonArray(
                                    fields
                                        .filter { it.type !is Nullable }
                                        .map { JsonPrimitive(it.name) }
                                ),
                        )
                    )
                }
                is Sealed ->
                    error("Sealed schema emission is not supported by kotlin-openai-schemas yet.")
            }

        /**
         * Adds [description] to a generated schema fragment.
         *
         * Primitive and union-like fragments are wrapped in an object with a `type` field; object
         * fragments are copied and augmented in place.
         *
         * @param json the schema fragment to annotate.
         * @param description optional description text.
         * @return a JSON object containing the original schema fields plus the description.
         */
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
    private fun fromClassDescriptor(
        descriptor: SerialDescriptor,
        cache: MutableMap<Ref<SerialDescriptor>, TypeDef>,
        typeName: KString? = null,
    ): TypeDef.Object {
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
            val fieldDocString =
                descriptor.getElementAnnotations(i).filterIsInstance<Doc>().firstOrNull()?.value
            val type = def(fieldDescriptor, cache)
            fields.add(TypeDef.Field(fieldName, type, fieldDocString))
        }

        val result = TypeDef.Object(name, fields, docString)
        cache[Ref(descriptor)] = result
        return result
    }

    /**
     * Derives a [TypeDef] from a Kotlin serialization descriptor.
     *
     * The [cache] is keyed by descriptor identity to preserve recursive/shared definitions and
     * avoid repeatedly expanding the same serializable type.
     *
     * @param descriptor the descriptor to inspect.
     * @param cache descriptor-identity cache reused across recursive calls.
     * @return the schema model for [descriptor].
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun def(
        descriptor: SerialDescriptor,
        cache: MutableMap<Ref<SerialDescriptor>, TypeDef>,
    ): TypeDef {
        if (Ref(descriptor) in cache) {
            return cache.getValue(Ref(descriptor))
        }

        val name = descriptor.serialName.split(".").last()
        val docString = descriptor.annotations.filterIsInstance<Doc>().firstOrNull()?.value

        val result: TypeDef =
            when (descriptor.kind) {
                is PrimitiveKind.CHAR -> TypeDef.Prim(PrimType.CHAR)
                is PrimitiveKind.STRING -> TypeDef.Prim(PrimType.STRING)
                is PrimitiveKind.BYTE -> TypeDef.Prim(PrimType.BYTE)
                is PrimitiveKind.SHORT -> TypeDef.Prim(PrimType.SHORT)
                is PrimitiveKind.INT -> TypeDef.Prim(PrimType.INT)
                is PrimitiveKind.LONG -> TypeDef.Prim(PrimType.LONG)
                is PrimitiveKind.FLOAT -> TypeDef.Prim(PrimType.FLOAT)
                is PrimitiveKind.DOUBLE -> TypeDef.Prim(PrimType.DOUBLE)
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
                    TypeDef.Enum(
                        name,
                        (0 until descriptor.elementsCount).map {
                            val valueName = descriptor.getElementName(it)
                            val valueDocString =
                                descriptor
                                    .getElementAnnotations(it)
                                    .filterIsInstance<Doc>()
                                    .firstOrNull()
                                    ?.value
                            TypeDef.EnumValue(valueName, valueDocString)
                        },
                        docString,
                    )
                }

                is StructureKind.CLASS ->
                    if (!descriptor.isInline) {
                        fromClassDescriptor(descriptor, cache)
                    } else {
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
                        check(
                            elemDesc.kind == StructureKind.CLASS ||
                                elemDesc.kind == StructureKind.OBJECT
                        )
                        val tpe = fromClassDescriptor(elemDesc, cache, name)
                        subtypes.add(name)
                    }

                    TypeDef.Sealed(name, subtypes, docString)
                }

                is SerialKind.CONTEXTUAL ->
                    error("Contextual serializers cannot be converted to OpenAI schemas.")
                PolymorphicKind.OPEN ->
                    error("Open polymorphic serializers cannot be converted to OpenAI schemas.")
            }

        if (descriptor.isNullable) {
            return TypeDef.Nullable(result)
        }

        // Don't store built-in types in the cache
        cache[Ref(descriptor)] = result
        return result
    }

    /**
     * Derives a [TypeDef] for the reified serializable type [T].
     *
     * @param cache descriptor-identity cache reused across recursive calls.
     * @return the schema model for [T].
     */
    inline fun <reified T> def(
        cache: MutableMap<Ref<SerialDescriptor>, TypeDef> = mutableMapOf()
    ): TypeDef = def(serializer<T>().descriptor, cache)

    /**
     * Builds OpenAI function tools from a sealed request descriptor.
     *
     * The descriptor must represent a non-null, non-inline sealed serializable type. Each subtype
     * is emitted as a separate OpenAI function tool. Subtypes should use `@SerialName` values
     * without dots because those names become OpenAI function names.
     *
     * @param descriptor the sealed request descriptor.
     * @return generated tool definitions in descriptor subtype order.
     * @throws IllegalArgumentException if [descriptor] is not a supported sealed request
     *   descriptor.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun makeFunctions(descriptor: SerialDescriptor): KList<ToolDef> {
        require(!descriptor.isInline) { "Top-level descriptor $descriptor must not be inline" }
        require(!descriptor.isNullable) { "Top-level descriptor $descriptor must not be nullable" }
        require(descriptor.kind == PolymorphicKind.SEALED) {
            "Top-level descriptor $descriptor must be sealed"
        }
        require(descriptor.getElementName(0) == "type") {
            "First element of $descriptor must be 'type'"
        }
        require(descriptor.getElementName(1) == "value") {
            "Second element of $descriptor must be 'value'"
        }
        val valueType = descriptor.getElementDescriptor(1)
        require(valueType.kind == SerialKind.CONTEXTUAL) {
            "Second element of $descriptor must be contextual"
        }

        val cache = mutableMapOf<Ref<SerialDescriptor>, TypeDef>()
        val result = mutableListOf<ToolDef>()

        for (i in 0 until valueType.elementsCount) {
            val name = valueType.getElementName(i)
            val elemDesc = valueType.getElementDescriptor(i)
            val description = elemDesc.annotations.filterIsInstance<Doc>().firstOrNull()?.value
            val annotations = elemDesc.annotations

            require('.' !in name) { "Invalid name: $name in $descriptor" }
            require(elemDesc.kind == StructureKind.CLASS || elemDesc.kind == StructureKind.OBJECT)

            val tpe =
                fromClassDescriptor(
                    elemDesc,
                    cache,
                    // NOTE: We explicitly set the typeName to null since we will be getting the
                    // type separately.
                    typeName = null,
                )

            if (tpe.fields.isEmpty()) {
                result.add(
                    ToolDef(
                        compiledToolSchema =
                            Tool(
                                type = ToolType.Function,
                                function = FunctionTool(name = name, description = description),
                            ),
                        annotations = annotations,
                    )
                )
                continue
            }

            result.add(
                ToolDef(
                    compiledToolSchema =
                        Tool(
                            type = ToolType.Function,
                            function =
                                FunctionTool(
                                    name = name,
                                    parameters = Parameters(tpe.toJsonSchema() as JsonObject),
                                    description = description,
                                ),
                        ),
                    annotations = annotations,
                )
            )
        }

        return result
    }

    /**
     * Decodes a model function call into a sealed request value.
     *
     * The function name is injected as the serialized `"type"` discriminator before decoding with
     * [serializer].
     *
     * @param serializer serializer for the sealed request type.
     * @param name the OpenAI function name to use as the sealed discriminator.
     * @param arguments parsed function-call arguments.
     * @return the decoded request value.
     */
    fun <Request> parseFunctionCall(
        serializer: KSerializer<Request>,
        name: String,
        arguments: JsonObject,
    ): Request {
        val fields = mutableMapOf<KString, JsonElement>("type" to JsonPrimitive(name))
        for ((k, v) in arguments) {
            fields[k] = v
        }
        return Json.decodeFromJsonElement(serializer, JsonObject(fields))
    }

    /**
     * Decodes a model function call from a JSON object string.
     *
     * @param serializer serializer for the sealed request type.
     * @param name the OpenAI function name to use as the sealed discriminator.
     * @param arguments JSON object string containing function-call arguments.
     * @return the decoded request value.
     */
    fun <Request> parseFunctionCall(
        serializer: KSerializer<Request>,
        name: String,
        arguments: String,
    ): Request = parseFunctionCall(serializer, name, Json.decodeFromString<JsonObject>(arguments))

    /**
     * Decodes an OpenAI SDK [FunctionCall] into a sealed request value.
     *
     * @param serializer serializer for the sealed request type.
     * @param req the function call returned by the model.
     * @return the decoded request value.
     */
    fun <Request> parseFunctionCall(serializer: KSerializer<Request>, req: FunctionCall): Request =
        parseFunctionCall(serializer, req.name, req.arguments)

    /**
     * Decodes an OpenAI SDK [FunctionCall] into a reified sealed request type.
     *
     * @param req the function call returned by the model.
     * @return the decoded request value.
     */
    inline fun <reified Request> parseFunctionCall(req: FunctionCall): Request =
        parseFunctionCall(serializer(), req)
}
