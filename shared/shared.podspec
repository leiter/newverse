Pod::Spec.new do |spec|
    spec.name                     = 'shared'
    spec.version                  = '1.0.0'
    spec.homepage                 = 'https://github.com/together/newverse'
    spec.source                   = { :http=> ''}
    spec.authors                  = ''
    spec.license                  = ''
    spec.summary                  = 'Newverse KMP shared library'
    spec.vendored_frameworks      = 'build/cocoapods/framework/shared.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '15.0'
    spec.dependency 'FirebaseAuth'
    spec.dependency 'FirebaseCore'
    spec.dependency 'FirebaseDatabase'
    spec.dependency 'FirebaseStorage'
    spec.dependency 'GoogleSignIn'
    if !Dir.exist?('build/cocoapods/framework/shared.framework') || Dir.empty?('build/cocoapods/framework/shared.framework')
        raise "
        Kotlin framework 'shared' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:
            ./gradlew :shared:generateDummyFramework
        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':shared',
        'PRODUCT_MODULE_NAME' => 'shared',
    }
    spec.script_phases = [
        {
            :name => 'Build shared',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                    echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                    exit 0
                fi
                set -ev

                # Locate Java — Xcode's PATH doesn't include JetBrains/Android Studio JDK.
                # Read org.gradle.java.home from ~/.gradle/gradle.properties if set (machine-specific).
                if [ -z "$JAVA_HOME" ]; then
                    GRADLE_USER_PROPS="$HOME/.gradle/gradle.properties"
                    if [ -f "$GRADLE_USER_PROPS" ]; then
                        JAVA_HOME_PROP=$(grep "^org.gradle.java.home=" "$GRADLE_USER_PROPS" | cut -d'=' -f2-)
                        if [ -n "$JAVA_HOME_PROP" ]; then
                            export JAVA_HOME="$JAVA_HOME_PROP"
                        fi
                    fi
                fi
                # Fallback: check standard Android Studio location
                if [ -z "$JAVA_HOME" ]; then
                    if [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
                        export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
                    elif /usr/libexec/java_home > /dev/null 2>&1; then
                        export JAVA_HOME=$(/usr/libexec/java_home)
                    fi
                fi
                export PATH="$JAVA_HOME/bin:$PATH"

                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
    spec.resources = ['build/compose/cocoapods/compose-resources']
end
