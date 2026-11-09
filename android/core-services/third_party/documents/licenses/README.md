# Document dependencies

PdfBox-Android 2.0.27.0 is the Android port of Apache PDFBox 2.0.27, primarily Apache-2.0. The AAR contains its resource/font assets. Optional JPX image support is not installed. PDF page previews use the platform renderer; this library does bounded text extraction and supported form/page edits in an isolated UID.

Its published POM requires Bouncy Castle bcprov/bcpkix/bcutil jdk15to18 1.72 (MIT-style Bouncy Castle license). All four original artifacts and POMs are pinned in sources.json with source URLs, sizes and SHA-256. Maven published SHA-1 values were checked during download in addition to retained SHA-256. These are old upstream versions, not a claim that upstream security fixes are current; the isolated worker has no network/storage authority and only bounded inherited FDs. Encrypted input and signature-changing edits are refused. Dependency security upgrades remain a release requirement.

Office reads/edits use AIOS bounded OOXML package handling, not Apache POI; it preserves untouched package entry bytes and does not implement full Office layout or formula calculation. See the document capability contract and acceptance report for verified limits.
