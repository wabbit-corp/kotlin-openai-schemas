# Development

## Build

```bash
./dev build kotlin-openai-schemas
```

The build covers JVM, Android library metadata, iOS simulator, iOS device, and host-native targets.

## Documentation Checks

```bash
./dev verify docs kotlin-openai-schemas
./gradlew -p kotlin-openai-schemas dokkaGeneratePublicationHtml
```

Public APIs should document descriptor expectations, emitted schema behavior, and unsupported
serializer shapes.

## Publication Dry Run

```bash
./dev publish --dry-run kotlin-openai-schemas
```

Generated Gradle metadata comes from `root.clj`. Update the root project definition and rerun
`./dev setup --local kotlin-openai-schemas` instead of editing generated Gradle files directly.

## Testing Guidance

Add tests with small sealed `@Serializable` request hierarchies. Cover both generated tool schemas
and `parseFunctionCall` decoding so schema names and serialization discriminators stay aligned.
