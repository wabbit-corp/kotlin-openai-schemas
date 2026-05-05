# Troubleshooting

## `makeFunctions` Rejects the Descriptor

Pass the descriptor for the sealed request hierarchy, not a single request subtype:

```kotlin
FunctionSchema.makeFunctions(ToolRequest.serializer().descriptor)
```

The descriptor must be non-null, non-inline, and sealed.

## Function Names Contain Dots

Default subtype serial names can include package-qualified names. Add `@SerialName("ToolName")` to
each request subtype so the generated OpenAI function name is short and contains no dots.

## Nullable Arguments Are Not Accepted by the Model

Nullable fields are omitted from the generated `required` list, but the current schema lowering does
not emit an explicit JSON Schema `"null"` union. If a model or API requires explicit nullable type
unions, add that support before relying on the generated schema for those fields.

## A Type Hits `TODO()`

Open polymorphism, contextual serializers, and direct sealed-type JSON Schema lowering are not
implemented. Prefer closed sealed request hierarchies and concrete serializable field types.

## Decoding Fails With Unknown Type

`parseFunctionCall` uses the function name as the sealed `"type"` discriminator. Confirm the name
matches the subtype `@SerialName` exactly.
