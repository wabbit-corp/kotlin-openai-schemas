# API Reference

Generated Dokka API docs can be built with:

```bash
./gradlew dokkaGeneratePublicationHtml
```

## FunctionSchema

`FunctionSchema` contains the public schema-generation and decoding helpers.

- `Doc`: descriptor-visible annotation for descriptions on request classes, enum values, and fields.
- `ToolDef`: generated OpenAI `Tool` plus the source subtype annotations.
- `FunctionDef`: serializable intermediate function definition.
- `PrimType`: primitive Kotlin serialization kinds understood by the schema model.
- `TypeDef`: intermediate schema tree for primitives, literals, enums, arrays, maps, aliases,
  objects, fields, sealed hierarchies, and nullable wrappers.

## Schema Generation

- `def(descriptor, cache)`: derives a `TypeDef` from a `SerialDescriptor`.
- `def<T>(cache)`: derives a `TypeDef` for a reified serializable type.
- `TypeDef.toJsonSchema()`: lowers supported schema nodes to OpenAI-compatible JSON Schema
  fragments.
- `TypeDef.addDescription(json, description)`: annotates schema fragments with descriptions.

`TypeDef.Sealed` is an intermediate representation. Use `makeFunctions` to turn sealed request
subtypes into separate OpenAI tools.

## Tool Generation

`makeFunctions(descriptor)` expects a non-null, non-inline sealed serializable descriptor. Each
subtype becomes one OpenAI function tool.

Subtype serial names must not contain dots. Add `@SerialName("ToolName")` to request subtypes if the
default serial name would include the package.

## Function-Call Decoding

- `parseFunctionCall(serializer, name, arguments: JsonObject)`: decodes parsed arguments.
- `parseFunctionCall(serializer, name, arguments: String)`: decodes a JSON object string.
- `parseFunctionCall(serializer, req: FunctionCall)`: decodes an OpenAI SDK function call.
- `parseFunctionCall<Request>(req: FunctionCall)`: reified convenience overload.

All overloads inject the function name as the sealed `"type"` discriminator before delegating to
`kotlinx.serialization`.

## Limitations

- Open polymorphism is not implemented.
- Contextual serializers are not implemented.
- Direct JSON Schema lowering for `TypeDef.Sealed` is not implemented.
- Nullable fields become optional object fields, but nullable types do not emit an explicit
  `"null"` union.
