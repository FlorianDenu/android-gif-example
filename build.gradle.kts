import com.android.build.gradle.internal.dsl.TestOptions

buildscript {
  applyFrom(rootProject.file("gradle/versions.gradle.kts"))

  rootProject.extra["ci"] = rootProject.hasProperty("ci")
  rootProject.extra["lollipop"] = rootProject.hasProperty("lollipop")

  repositories {
    google()
    maven { setUrl("https://plugins.gradle.org/m2/") }
  }

  dependencies {
    classpath(extra["gradle"])
    classpath(extra["kotlinGradlePlugin"])
    classpath(extra["gradleAndroidCommandPlugin"])
    classpath(extra["playPublisher"])
    classpath(extra["buildScanPlugin"])
    classpath(extra["dexcountGradlePlugin"])
    classpath(extra["gradleAndroidApkSizePlugin"])
    classpath(extra["coverallsGradlePlugin"])
    classpath(extra["gradleVersionsPlugin"])
    classpath(extra["gradleLicensePlugin"])
  }
}

repositories {
  google()
  jcenter()
}

apply {
  from(rootProject.file("gradle/scan.gradle"))
  plugin("com.android.application")
  plugin("kotlin-android")
  plugin("kotlin-kapt")
  plugin("android-command")
  plugin("com.github.triplet.play")
  plugin("com.getkeepsafe.dexcount")
  plugin("com.vanniktech.android.apk.size")
  plugin("com.github.ben-manes.versions")
  plugin("com.jaredsburrows.license")
  from(rootProject.file("gradle/compile.gradle.kts"))
  from(rootProject.file("gradle/quality.gradle"))
  from(rootProject.file("gradle/publish.gradle"))
  from(rootProject.file("gradle/wrapper.gradle.kts"))
}

android {
  compileSdkVersion(extra["compileSdkVersion"] as Int)
  buildToolsVersion(extra["buildToolsVersion"] as String)

  defaultConfig {
    applicationId = "burrows.apps.example.gif"
    versionCode = 1
    versionName = "1.0"
    minSdkVersion(if (rootProject.extra["lollipop"] as Boolean) 21 else rootProject.extra["minSdkVersion"] as Int) // Optimize build speed - build with minSdk 21 if using multidex
    targetSdkVersion(rootProject.extra["targetSdkVersion"] as Int)
    testApplicationId = "burrows.apps.example.gif.test"
    testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    resConfigs("en")                                                            // Optimize APK size - keep only english resource files for now
    vectorDrawables.useSupportLibrary = true                                    // Optimize APK size - use vector drawables
  }

  dataBinding {
    isEnabled = true
    isEnabledForTests = true
  }

  compileOptions {
    setSourceCompatibility(extra["sourceCompatibilityVersion"])
    setTargetCompatibility(extra["targetCompatibilityVersion"])
  }

  // Optimize local build speed - disable aapt PNG crunching
  aaptOptions {
    cruncherEnabled = extra["ci"] as Boolean
  }

  // Optimize ci build speed - disable dexing on ci
  dexOptions {
    preDexLibraries = !(extra["ci"] as Boolean)
  }

  // Need this to help IDE recognize Kotlin
//  sourceSets {
//    androidTest.java.srcDirs += "src/androidTest/kotlin"
//    debug.java.srcDirs += "src/debug/kotlin"
//    main.java.srcDirs += "src/main/kotlin"
//    test.java.srcDirs += "src/test/kotlin"
//  }
//  sourceSets["main"].java.srcDirs.add("src/main/kotlin")
//  println(sourceSets["main"].java.srcDirs)

  lintOptions {
    textReport = true
    textOutput("stdout")
    isCheckAllWarnings = true
    isWarningsAsErrors = true
    setLintConfig(rootProject.file("${project.rootDir}/config/lint/lint.xml"))
  }

  // Add "debug.keystore" so developers can share APKs with same signatures locally
  signingConfigs {
    getByName("debug") {
      storeFile = rootProject.file("config/signing/debug.keystore")
      storePassword = extra["debugKeystorePass"] as String
      keyAlias = extra["debugKeystoreUser"] as String
      keyPassword = extra["debugKeystorePass"] as String
    }
  }

  buildTypes {
    getByName("debug") {
      if (extra["ci"] as Boolean) isTestCoverageEnabled = true                      // https://issuetracker.google.com/issues/37019591
      applicationIdSuffix = ".debug"

      if (extra["ci"] as Boolean) {
        buildConfigField("String", "BASE_URL", "\"http://localhost:8080\"")
      } else {
        buildConfigField("String", "BASE_URL", "\"https://api.riffsy.com\"")
      }
    }

    // Apply fake signing config to release to test "assembleRelease" locally
    getByName("release") {
      isMinifyEnabled = true                                                        // Optimize APK size - remove/optimize DEX file(s)
      isShrinkResources = true                                                      // Optimize APK size - remove unused resources
      proguardFile(getDefaultProguardFile("proguard-android-optimize.txt"))         // Optimize APK size - use optimized proguard rules
      proguardFile(rootProject.file("config/proguard/proguard-rules.txt"))
      signingConfig = signingConfigs.getByName("debug")

      buildConfigField("String", "BASE_URL", "\"https://api.riffsy.com\"")
    }
  }

  testOptions {
    animationsDisabled = true
    unitTests(delegateClosureOf<TestOptions.UnitTestOptions>{
      setReturnDefaultValues(true)
      all(KotlinClosure1<Any, Test>({
        (this as Test).also { testTask ->
          testTask.extensions
            .getByType(JacocoTaskExtension::class.java)
            .setIncludeNoLocationClasses(true)
        }
      }, this))
    })
  }

  // Optimize APK size - remove excess files in the manifest and APK
  packagingOptions {
    exclude("/META-INF/*.kotlin_module")
    exclude("**/kotlin/**")
    exclude("**/*.txt")
    exclude("**/*.xml")
    exclude("**/*.properties")
  }
}

// Resolves dependency versions across test and production APKs, specifically, transitive
// dependencies. This is required since Espresso internally has a dependency on support-annotations.
configurations.all {
  resolutionStrategy.force(extra["supportAnnotations"])
  resolutionStrategy.force(extra["kotlinStdlib"])
}

dependencies {
  compile(extra["design"])
  compile(extra["cardviewv7"])
  compile(extra["kotlinStdlib"])
  compile(extra["okhttp"])
  compile(extra["loggingInterceptor"])
  compile(extra["adapterRxjava2"])
  compile(extra["converterMoshi"])
  compile(extra["moshiAdapters"])
  compile(extra["retrofit"])
  compile(extra["rxAndroid"])
  compile(extra["rxJava"])
  compile(extra["glide"])
  compile(extra["okhttp3Integration"])
  compile(extra["dagger"])

  kapt(extra["daggerCompiler"])
  kapt(extra["compiler"])

  debugCompile(extra["leakcanaryAndroid"])
  releaseCompile(extra["leakcanaryAndroidNoOp"])

  androidTestCompile(extra["junit"])
  androidTestCompile(extra["assertjCore"])
  androidTestCompile(extra["mockitoKotlin"] as String) { exclude(group = "net.bytebuddy") }     // DexMaker has it"s own MockMaker
  androidTestCompile(extra["mockitoCore"] as String) { exclude(group = "net.bytebuddy") }       // DexMaker has it"s own MockMaker
  androidTestCompile(extra["dexmakerMockito"] as String) { exclude(group = "net.bytebuddy") }   // DexMaker has it"s own MockMaker
  androidTestCompile(extra["runner"])
  androidTestCompile(extra["espressoCore"])
  androidTestCompile(extra["espressoIntents"])
  androidTestCompile(extra["espressoContrib"] as String) { exclude(group = "com.android.support") }
  androidTestCompile(extra["mockwebserver"])

  testCompile(extra["junit"])
  testCompile(extra["assertjCore"])
  testCompile(extra["mockitoKotlin"])
  testCompile(extra["mockitoCore"])
  testCompile(extra["leakcanaryAndroidNoOp"])
  testCompile(extra["mockwebserver"])
  testCompile(extra["equalsverifier"])
  testCompile(extra["reflections"])
}
