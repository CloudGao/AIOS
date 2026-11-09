# Included by the selected device BoardConfig, after device configuration.
ifneq ($(TARGET_ARCH),arm64)
$(error AIOS integration requires TARGET_ARCH=arm64)
endif
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += vendor/aios/sepolicy/system_ext/private
