package one.wabbit.openaischemas

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import one.wabbit.data.Ref
import kotlin.test.Test
import one.wabbit.openaischemas.FunctionSchema.Doc


@Serializable @JvmInline
value class ParagraphId(val value: Int)

@Serializable
enum class Genre {
    @Doc("Science fiction") SCI_FI,
    @Doc("Fantasy") FANTASY,
    @Doc("Mystery") MYSTERY,
}

@Serializable
sealed interface StoryElement {
    @Serializable
    data class Paragraph(
        @SerialName("paragraphId")
        val id: ParagraphId,
        @Doc("The text of the paragraph")
        val text: String) : StoryElement
    @Serializable
    data class Image(val url: String) : StoryElement
    @Serializable
    data object Separator : StoryElement
}

@Serializable
data class Character(
    @Doc("The name of the character")
    val name: String,
    @Doc("The description of the character")
    val description: String,
    @Doc("The image of the character")
    val imageURL: String? = null)

@Serializable
data class Story(
    @Doc("The title of the story")
    val title: String,
    @Doc("The genre of the story")
    val genre: Genre,
    @Doc("The elements of the story: paragraphs and images")
    val elements: List<StoryElement>,
    @Doc("Characters in the story")
    val characters: Map<String, Character>
)

sealed interface TSType {
    data class Prim(val name: String) : TSType
    data class Array(val elementType: TSType) : TSType
    data class Map(val keyType: TSType, val valueType: TSType) : TSType

    data class Alias(val name: String, val type: TSType, val docString: String?) : TSType

    data class EnumValue(val name: String, val docString: String?)
    data class Enum(val name: String, val values: List<EnumValue>, val docString: String?) : TSType

    data class ObjectField(val name: String, val type: TSType, val docString: String?)
    data class Object(val name: String, val fields: List<ObjectField>, val docString: String?) : TSType
    data class Sealed(val name: String, val subtypes: List<String>, val docString: String?) : TSType

    data class Nullable(val type: TSType) : TSType

    fun toName(): String = when (this) {
        is Prim -> name
        is Array -> "${elementType.toName()}[]"
        is Map -> "{ [key: ${keyType.toName()}]: ${valueType.toName()} }"
        is Enum -> name
        is Object -> name
        is Sealed -> name
        is Alias -> name
        is Nullable -> "${type.toName()} | null"
    }

    fun toDefinition(): String = when (this) {
        is Prim, is Array, is Map, is Nullable -> TODO()
        is Enum -> "type $name = ${values.joinToString(" | ") { escapeTS(it.name) }}"
        is Object -> {
            val declDoc = if (docString != null) "// $docString\n" else ""

            val fields = fields.joinToString("\n") {
                val fieldDoc = if (it.docString != null) "    // ${it.docString.trim()}\n" else ""
                fieldDoc + "    ${it.name}: ${it.type.toName()}"
            }
            declDoc + "interface $name {\n$fields\n}"
        }
        is Sealed -> {
            val declDoc = if (docString != null) "// ${docString.trim()}\n" else ""
            val subtypes = subtypes.joinToString(" | ")
            declDoc + "type $name = $subtypes"
        }
        is Alias -> {
            val declDoc = if (docString != null) "// ${docString.trim()}\n" else ""
            declDoc + "type $name = ${type.toName()}"
        }
    }

//    fun toJsonSchema(): JsonElement = when (this) {
//        is Prim -> JsonPrimitive(name)
//        is Array -> JsonObject(mapOf("type" to JsonPrimitive("array"), "items" to elementType.toJsonSchema()))
//        is Map -> JsonObject(mapOf("type" to JsonPrimitive("object"), "additionalProperties" to valueType.toJsonSchema()))
//        is Enum -> JsonObject(mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(
//            values.map { JsonPrimitive(it.name) }
//        )))
//        is Object -> JsonObject(mapOf("type" to JsonPrimitive("object"), "properties" to JsonObject(
//            fields.associate { it.name to it.type.toJsonSchema() }
//        )))
//        is Sealed -> JsonObject(mapOf("type" to JsonPrimitive("string"), "enum" to JsonArray(
//            subtypes.map { JsonPrimitive(it) }
//        )))
//        is Alias -> type.toJsonSchema()
//
//    }
}

fun escapeTS(s: String): String {
    val sb = StringBuilder()
    sb.append("\"")
    for (c in s) {
        when (c) {
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            '\\' -> sb.append("\\\\")
            '\'' -> sb.append("\\'")
            '\"' -> sb.append("\\\"")
            else -> sb.append(c)
        }
    }
    sb.append("\"")
    return sb.toString()
}

@OptIn(ExperimentalSerializationApi::class)
private fun fromClassDescriptor(descriptor: SerialDescriptor, cache: MutableMap<Ref<SerialDescriptor>, TSType>, typeName: String? = null): TSType.Object {
    if (Ref(descriptor) in cache) {
        return cache.getValue(Ref(descriptor)) as TSType.Object
    }

    val name = descriptor.serialName.split(".").last()
    val docString = descriptor.annotations.filterIsInstance<Doc>().firstOrNull()?.value

    check(descriptor.kind == StructureKind.OBJECT || descriptor.kind == StructureKind.CLASS)

    if (descriptor.kind == StructureKind.OBJECT) {
        return TSType.Object(name, emptyList(), docString)
    }

    val fields = mutableListOf<TSType.ObjectField>()
    if (typeName != null) {
        fields.add(TSType.ObjectField("type", TSType.Prim("\"$typeName\""), null))
    }

    for (i in 0 until descriptor.elementsCount) {
        val fieldDescriptor = descriptor.getElementDescriptor(i)
        val fieldName = descriptor.getElementName(i)
        val fieldDocString = descriptor.getElementAnnotations(i).filterIsInstance<Doc>().firstOrNull()?.value
        val type = def(fieldDescriptor, cache)
        fields.add(TSType.ObjectField(fieldName, type, fieldDocString))
    }

    val result = TSType.Object(name, fields, docString)
    cache[Ref(descriptor)] = result
    return result
}

@OptIn(ExperimentalSerializationApi::class)
fun def(descriptor: SerialDescriptor, cache: MutableMap<Ref<SerialDescriptor>, TSType>): TSType {
    if (Ref(descriptor) in cache) {
        return cache.getValue(Ref(descriptor))
    }

    val name = descriptor.serialName.split(".").last()
    val docString = descriptor.annotations.filterIsInstance<Doc>().firstOrNull()?.value

    val result = when (descriptor.kind) {
        is PrimitiveKind.CHAR    -> TSType.Prim("string")
        is PrimitiveKind.STRING  -> TSType.Prim("string")
        is PrimitiveKind.BYTE    -> TSType.Prim("number")
        is PrimitiveKind.SHORT   -> TSType.Prim("number")
        is PrimitiveKind.INT     -> TSType.Prim("number")
        is PrimitiveKind.LONG    -> TSType.Prim("number")
        is PrimitiveKind.FLOAT   -> TSType.Prim("number")
        is PrimitiveKind.DOUBLE  -> TSType.Prim("number")
        is PrimitiveKind.BOOLEAN -> TSType.Prim("number")
        is StructureKind.LIST -> {
            val e = descriptor.getElementDescriptor(0)
            val type = def(e, cache)
            TSType.Array(type)
        }
        is StructureKind.MAP -> {
            val key = descriptor.getElementDescriptor(0)
            val value = descriptor.getElementDescriptor(1)
            val keyType = def(key, cache)
            val valueType = def(value, cache)
            TSType.Map(keyType, valueType)
        }

        is SerialKind.ENUM -> {
            // type Foo = "a" | "b"
            TSType.Enum(name, (0 until descriptor.elementsCount).map {
                val valueName = descriptor.getElementName(it)
                val valueDocString = descriptor.getElementAnnotations(it).filterIsInstance<FunctionSchema.Doc>().firstOrNull()?.value
                TSType.EnumValue(valueName, valueDocString)
            }, docString)
        }

        is StructureKind.CLASS ->
            if (!descriptor.isInline) fromClassDescriptor(descriptor, cache)
            else {
                check(descriptor.elementsCount == 1)
                val elemDesc = descriptor.getElementDescriptor(0)
                val type = def(elemDesc, cache)
                TSType.Alias(name, type, docString)
            }
        StructureKind.OBJECT -> fromClassDescriptor(descriptor, cache)

        PolymorphicKind.SEALED -> {
            check(descriptor.getElementName(0) == "type")
            check(descriptor.getElementName(1) == "value")
            val valueType = descriptor.getElementDescriptor(1)
            check(valueType.kind == SerialKind.CONTEXTUAL)

            val subtypes = mutableListOf<String>()
            for (i in 0 until valueType.elementsCount) {
                val serialName = valueType.getElementName(i)
                val name = serialName.split(".").last()
                val elemDesc = valueType.getElementDescriptor(i)
                check(elemDesc.kind == StructureKind.CLASS || elemDesc.kind == StructureKind.OBJECT)
                val tpe = fromClassDescriptor(elemDesc, cache, name)
                subtypes.add(name)
            }

            TSType.Sealed(name, subtypes, docString)
        }

        is SerialKind.CONTEXTUAL -> TODO()
        PolymorphicKind.OPEN -> TODO()
    }

    if (descriptor.isNullable) {
        return TSType.Nullable(result)
    }

    // Don't store built-in types in the cache
    cache[Ref(descriptor)] = result
    return result
}

inline fun <reified T> def(cache: MutableMap<Ref<SerialDescriptor>, TSType> = mutableMapOf()): TSType {
    return def(serializer<T>().descriptor, cache)
}

class TestSerializable {
    @Test
    fun test() {
        val cache = LinkedHashMap<Ref<SerialDescriptor>, TSType>()
        val def = def<Story>(cache)

        for (entry in cache.values) {
            if (entry is TSType.Prim || entry is TSType.Array || entry is TSType.Map)
                continue
            println(entry.toDefinition())
            println()
        }
    }
}
