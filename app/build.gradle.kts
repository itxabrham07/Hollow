import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.isovault.xrvf"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    buildConfigField(
      "String",
      "HOLLOW_MODULE_PUBLIC_KEY_B64",
      "\"MCowBQYDK2VwAyEArBn2TtGmUR4akTCBxgbp/vt5As1C9bQSPces4mh9w3M=\""
    )
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  compileOnly(files("libs/xposed-api-82.jar"))
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("compileFeatureModules") {
  group = "hollow"
  description = "Compiles standalone feature modules in modules-src/<module_id>/ into module.dex.bin and signs them with Ed25519."
  dependsOn("compileDebugKotlin")
  notCompatibleWithConfigurationCache("Compiles external modules-src directories with kotlinc and d8")

  val repoRootDir = rootDir
  val compilerCpFiles: org.gradle.api.file.FileCollection = configurations.getByName("kotlinCompilerClasspath")
  val kotlinTaskProvider = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileDebugKotlin")
  val pluginCpProvider = kotlinTaskProvider.map { it.pluginClasspath }
  val appClassesDirProvider = kotlinTaskProvider.flatMap { it.destinationDirectory }
  val libFilesProvider = kotlinTaskProvider.map { it.libraries }

  doLast {
    val compilerCp = compilerCpFiles.files
    val pluginCp = pluginCpProvider.get().files
    val appClassesDir = appClassesDirProvider.get().asFile
    val libFiles = libFilesProvider.get().files

    val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: "/opt/android/sdk"
    val d8Bin = File(sdkRoot, "build-tools/36.0.0/d8")
    val javaHome = System.getProperty("java.home")
    val javaBin = File(javaHome, "bin/java")
    val jarBin = File(javaHome, "bin/jar")

    val modulesSrcRoot = File(repoRootDir, "modules-src")
    val moduleDirs = modulesSrcRoot.listFiles()
      ?.filter { it.isDirectory }
      ?.sortedBy { it.name }
      ?: emptyList()

    val fullCompileClasspath = (libFiles + appClassesDir).joinToString(File.pathSeparator) { it.absolutePath }
    val pluginArg = "-Xplugin=${pluginCp.joinToString(",") { it.absolutePath }}"

    for (modDir in moduleDirs) {
      val modId = modDir.name
      val ktFiles = modDir.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("/build/") }.toList()
      if (ktFiles.isEmpty()) continue

      val modBuildDir = File(modDir, "build")
      val classesDir = File(modBuildDir, "classes")
      if (classesDir.exists()) classesDir.deleteRecursively()
      classesDir.mkdirs()

      val kotlincCmd = mutableListOf(
        javaBin.absolutePath,
        "-cp",
        compilerCp.joinToString(File.pathSeparator) { f -> f.absolutePath },
        "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
        "-no-stdlib",
        "-jvm-target",
        "11",
        pluginArg,
        "-cp",
        fullCompileClasspath,
        "-d",
        classesDir.absolutePath
      )
      ktFiles.forEach { kotlincCmd.add(it.absolutePath) }

      val kotlincProc = ProcessBuilder(kotlincCmd)
        .redirectErrorStream(true)
        .start()
      val kotlincOut = kotlincProc.inputStream.bufferedReader().readText()
      val kotlincExit = kotlincProc.waitFor()
      if (kotlincExit != 0) {
        throw GradleException("kotlinc failed for $modId (exit $kotlincExit):\n$kotlincOut")
      }

      // Package compiled module classes into module-classes.jar
      val jarFile = File(modBuildDir, "module-classes.jar")
      if (jarFile.exists()) jarFile.delete()
      val jarProc = ProcessBuilder(
        jarBin.absolutePath,
        "cf",
        jarFile.absolutePath,
        "-C",
        classesDir.absolutePath,
        "."
      ).redirectErrorStream(true).start()
      val jarOut = jarProc.inputStream.bufferedReader().readText()
      if (jarProc.waitFor() != 0) {
        throw GradleException("jar failed for $modId:\n$jarOut")
      }

      // Convert module-classes.jar into standalone DEX (module.dex.bin) using Android SDK d8
      val d8Cmd = mutableListOf(
        d8Bin.absolutePath,
        "--min-api",
        "24",
        "--output",
        modBuildDir.absolutePath,
        "--classpath",
        appClassesDir.absolutePath
      )
      libFiles.filter { it.exists() && it.extension == "jar" }.forEach { jar ->
        d8Cmd.add("--classpath")
        d8Cmd.add(jar.absolutePath)
      }
      d8Cmd.add(jarFile.absolutePath)

      val d8Proc = ProcessBuilder(d8Cmd)
        .redirectErrorStream(true)
        .start()
      val d8Out = d8Proc.inputStream.bufferedReader().readText()
      val d8Exit = d8Proc.waitFor()
      if (d8Exit != 0) {
        throw GradleException("d8 failed for $modId (exit $d8Exit):\n$d8Out")
      }

      val classesDex = File(modBuildDir, "classes.dex")
      val outDexBin = File(modBuildDir, "module.dex.bin")
      if (!classesDex.exists()) {
        throw GradleException("Expected classes.dex not generated for $modId")
      }
      if (outDexBin.exists()) outDexBin.delete()
      classesDex.renameTo(outDexBin)
      println("[compileFeatureModules] Built ${outDexBin.relativeTo(repoRootDir)} (${outDexBin.length()} bytes)")
    }

    // Sign all compiled module.dex.bin files with Ed25519 and sync to /modules/<id>/
    val signProc = ProcessBuilder("node", "${repoRootDir}/scripts/sign-modules.js")
      .directory(repoRootDir)
      .redirectErrorStream(true)
      .start()
    val signOut = signProc.inputStream.bufferedReader().readText()
    val signExit = signProc.waitFor()
    println(signOut)
    if (signExit != 0) {
      throw GradleException("sign-modules.js failed (exit $signExit)")
    }
  }
}


