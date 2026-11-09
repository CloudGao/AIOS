# AIOS source modules for the LineageOS ARM64 product.
PRODUCT_PACKAGES += AIOSCoreServices
PRODUCT_PACKAGES += AIOSControlAgent
PRODUCT_PACKAGES += AIOSMiniApps
PRODUCT_PACKAGES += AIOSCodexRuntime
PRODUCT_PACKAGES += agent-browser
PRODUCT_PACKAGES += AIOSSpeech
PRODUCT_PACKAGES += AIOSKokoroTts

# Development ADB keys and USB-init overrides must never enter a release.
include vendor/aios/prebuilts/prebuilts.mk
include vendor/aios/search-models/models.mk
