# Dextop verify gate (Flutter / Dart, Kotlin via Gradle)
# Bootstrap adapted the Turboplan Go seed to this stack.
#
# make verify        - analyze + test + build (default gate; lefthook pre-commit)
# make install-hooks - lefthook install (pre-commit -> make verify)
#
# Build flavor can be overridden:
#   make build BUILD_ARGS="--debug --target-platform android-arm64"

FLUTTER ?= flutter

.PHONY: help analyze test build verify fmt fmt-check clean install-hooks

help:
	@echo "Targets:"
	@echo "  make verify        - analyze + test + build (default gate; lefthook pre-commit)"
	@echo "  make analyze       - flutter analyze (flutter_lints via analysis_options.yaml)"
	@echo "  make test          - flutter test"
	@echo "  make build         - flutter build apk --debug (override: BUILD_ARGS=...)"
	@echo "  make fmt           - dart format lib test (applies changes)"
	@echo "  make fmt-check     - dart format --set-exit-if-changed lib test (no writes)"
	@echo "  make clean         - flutter clean"
	@echo "  make install-hooks - lefthook install (pre-commit -> make verify)"

analyze:
	$(FLUTTER) analyze

test:
	$(FLUTTER) test

BUILD_ARGS ?= apk --debug --flavor github
build:
	$(FLUTTER) build $(BUILD_ARGS)

verify: analyze test build

fmt:
	$(FLUTTER) dart format lib test

fmt-check:
	$(FLUTTER) dart format --set-exit-if-changed lib test

clean:
	$(FLUTTER) clean

install-hooks:
	lefthook install
