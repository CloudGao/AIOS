# Unified search dependencies

LiteRT 2.3.0: official Google Maven litert, litert-api and litert-gpu AARs. Original POM/AAR, extracted ARM64 runtime and licenses are retained; provenance.json pins source URLs and SHA256.

litert-offline-api.jar contains only com/google/ai/edge/litert classes excluding deployment/, ModelProvider and ModelSelector. It excludes Play AI Delivery and download orchestration. CompiledModel, Environment, TensorBuffer and their required value types remain. Kotlin runtime is supplied by the Lineage build. Native dependencies were inspected with readelf; libc, libm, libdl, liblog, libandroid, EGL and GLES are platform libraries.

sqlite-vec v0.1.6: official tagged source and MIT/Apache licenses; sqlite-vec.h is generated from the upstream template with version 0.1.6. Built with SQLITE_CORE and SQLITE_VEC_OMIT_FS and linked to a dedicated SQLite 3.50.6 amalgamation copied from the fixed Lineage tree, without platform ICU hooks. Java SQLiteOpenHelper never loads a dynamic extension.

Models are separate protected files, not bundled photos. tools/search/export_models.py pins the MobileCLIP2-S0 checkpoint and produces paired towers, tokenizer and space manifest. Model setup and acceptance evidence are recorded separately; downloading a model does not validate its GPU backend.
