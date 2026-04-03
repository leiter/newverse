fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## iOS

### ios test

```sh
[bundle exec] fastlane ios test
```

Run tests

### ios build_simulator

```sh
[bundle exec] fastlane ios build_simulator
```

Build for simulator (debug)

### ios build_shared_framework

```sh
[bundle exec] fastlane ios build_shared_framework
```

Build shared KMP framework

### ios build

```sh
[bundle exec] fastlane ios build
```

Build release for device

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Upload to TestFlight

### ios deploy

```sh
[bundle exec] fastlane ios deploy
```

Deploy to App Store

### ios deliver_metadata

```sh
[bundle exec] fastlane ios deliver_metadata
```

Upload metadata and screenshots only

### ios bump_build

```sh
[bundle exec] fastlane ios bump_build
```

Increment build number

### ios bump_version

```sh
[bundle exec] fastlane ios bump_version
```

Increment version number

### ios sync_certificates

```sh
[bundle exec] fastlane ios sync_certificates
```

Sync certificates and provisioning profiles

### ios register_devices_and_regenerate

```sh
[bundle exec] fastlane ios register_devices_and_regenerate
```

Register new devices and regenerate profiles

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
