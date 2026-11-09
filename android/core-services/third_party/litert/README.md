# LiteRT 2.3.0 inputs

The original AAR files and their individual classes.jar/native members are pinned in dependencies.lock.json. Preserve their LICENSE and THIRD_PARTY_NOTICE files.

AIOS uses litert-offline-api.jar as a selected assembly of 44 unchanged members from the three upstream classes.jar files, plus one directory entry. It is not the upstream AAR itself.

After restoring the three classes.jar inputs, run from the AIOS repository root:

```sh
python3 tools/distribution/rebuild-litert-api.py
```

The recipe tools/distribution/litert-layout.json records input, member and output hashes and ZIP metadata. Rebuilding was verified byte-for-byte against the pinned JAR. Conflicting destinations are preserved. This reconstruction does not rebuild the native LiteRT libraries or change their license obligations.
