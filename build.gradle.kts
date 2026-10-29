// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// -----------------------------------------------------------------------------
// Phase 6: Guardrail task verifyCoreIsEmpty
// FAILS the build if Core APK contains feature screens, hooks, or module_* IDs
// -----------------------------------------------------------------------------
tasks.register("verifyCoreIsEmpty") {
  group = "verification"
  description = "Verifies that Core APK contains zero feature screens, feature hooks, or module_* references."
  notCompatibleWithConfigurationCache("Scans source files dynamically")
  val repoRootDir = rootDir
  doLast {
    val appSrc = File(repoRootDir, "app/src/main/java")
    val forbiddenScreenNames = setOf(
      "AppHiderScreen", "VpnProxyScreen", "DeviceSpooferScreen",
      "ConfigurePrivacyCategorizedScreen", "AppsScreen", "FilesScreen",
      "GalleryScreen", "VaultScreen"
    )
    val forbiddenHookNames = setOf(
      "StorageIsolationHook", "DeviceIdentitySpoofHook", "SystemStealthHook"
    )
    val forbiddenIds = listOf(
      "\"module_hider\"", "\"module_network\"", "\"module_privacy\"", "\"module_isolation\"", "\"module_sensor_firewall\""
    )

    val violations = mutableListOf<String>()

    appSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
      val content = file.readText()
      val baseName = file.nameWithoutExtension

      if (forbiddenScreenNames.contains(baseName)) {
        violations.add("Forbidden feature screen found in core: ${file.relativeTo(rootDir)}")
      }
      if (forbiddenHookNames.contains(baseName)) {
        violations.add("Forbidden feature hook found in core: ${file.relativeTo(rootDir)}")
      }
      for (forbidden in forbiddenIds) {
        if (content.contains(forbidden)) {
          violations.add("Hardcoded module ID reference $forbidden found in ${file.relativeTo(rootDir)}")
        }
      }
    }

    if (violations.isNotEmpty()) {
      throw GradleException("verifyCoreIsEmpty FAILED! Found ${violations.size} violations in Core APK:\n" + violations.joinToString("\n"))
    } else {
      println("[verifyCoreIsEmpty] PASSED: Core APK has ZERO feature screens, feature hooks, or hardcoded module IDs.")
    }
  }
}

// -----------------------------------------------------------------------------
// Phase 2: Independent module project build tasks
// Compiles modules against :hollow-sdk ONLY (never requires compiling or touching :app)
// -----------------------------------------------------------------------------
fun compileAndSignModule(modDir: File, repoRootDir: File, sdkProject: Project) {
  val modId = modDir.name
  val ktFiles = modDir.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("/build/") }.toList()
  if (ktFiles.isEmpty()) return

  val modBuildDir = File(modDir, "build")
  val classesDir = File(modBuildDir, "classes")
  if (classesDir.exists()) classesDir.deleteRecursively()
  classesDir.mkdirs()

  val sdkRoot = System.getenv("ANDROID_SDK_ROOT") ?: "/opt/android/sdk"
  val d8Bin = File(sdkRoot, "build-tools/36.0.0/d8")
  val androidJar = File(sdkRoot, "platforms/android-36/android.jar")
  val javaHome = System.getProperty("java.home")
  val javaBin = File(javaHome, "bin/java")
  val jarBin = File(javaHome, "bin/jar")

  val sdkClassesJar = File(sdkProject.layout.buildDirectory.get().asFile, "intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar")
  val kotlinCompileTask = sdkProject.tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileDebugKotlin").get()
  val sdkCompileCp = kotlinCompileTask.libraries.files
  val compilerCp = sdkProject.configurations.getByName("kotlinCompilerClasspath").files
  val pluginCp = kotlinCompileTask.pluginClasspath.files

  val compileClasspath = (listOf(androidJar, sdkClassesJar) + sdkCompileCp).filter { it.exists() }.joinToString(File.pathSeparator) { it.absolutePath }
  val pluginArg = "-Xplugin=${pluginCp.joinToString(",") { it.absolutePath }}"

  println("[buildModule] Compiling $modId against :hollow-sdk...")
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
    compileClasspath,
    "-d",
    classesDir.absolutePath
  )
  ktFiles.forEach { kotlincCmd.add(it.absolutePath) }

  val kotlincProc = ProcessBuilder(kotlincCmd).redirectErrorStream(true).start()
  val kotlincOut = kotlincProc.inputStream.bufferedReader().readText()
  val kotlincExit = kotlincProc.waitFor()
  if (kotlincExit != 0) {
    throw GradleException("kotlinc failed for $modId (exit $kotlincExit):\n$kotlincOut")
  }

  // Package compiled classes into module-classes.jar
  val jarFile = File(modBuildDir, "module-classes.jar")
  if (jarFile.exists()) jarFile.delete()
  val jarProc = ProcessBuilder(jarBin.absolutePath, "cf", jarFile.absolutePath, "-C", classesDir.absolutePath, ".").redirectErrorStream(true).start()
  val jarOut = jarProc.inputStream.bufferedReader().readText()
  if (jarProc.waitFor() != 0) {
    throw GradleException("jar packaging failed for $modId:\n$jarOut")
  }

  // Convert to standalone DEX (module.dex.bin) with d8
  val d8Cmd = mutableListOf(
    d8Bin.absolutePath,
    "--min-api",
    "24",
    "--output",
    modBuildDir.absolutePath,
    "--classpath",
    androidJar.absolutePath,
    "--classpath",
    sdkClassesJar.absolutePath
  )
  sdkCompileCp.filter { it.exists() && it.extension == "jar" }.forEach { j ->
    d8Cmd.add("--classpath")
    d8Cmd.add(j.absolutePath)
  }
  d8Cmd.add(jarFile.absolutePath)

  val d8Proc = ProcessBuilder(d8Cmd).redirectErrorStream(true).start()
  val d8Out = d8Proc.inputStream.bufferedReader().readText()
  val d8Exit = d8Proc.waitFor()
  if (d8Exit != 0) {
    throw GradleException("d8 dexing failed for $modId (exit $d8Exit):\n$d8Out")
  }

  val classesDex = File(modBuildDir, "classes.dex")
  val outDexBin = File(modBuildDir, "module.dex.bin")
  if (classesDex.exists()) {
    if (outDexBin.exists()) outDexBin.delete()
    classesDex.renameTo(outDexBin)
  }

  // Sign with Ed25519 and sync to /modules/<modId>/
  val signProc = ProcessBuilder("node", "${repoRootDir}/scripts/sign-modules.js", modId)
    .directory(repoRootDir)
    .redirectErrorStream(true)
    .start()
  val signOut = signProc.inputStream.bufferedReader().readText()
  val signExit = signProc.waitFor()
  println(signOut)
  if (signExit != 0) {
    throw GradleException("Module signing failed for $modId (exit $signExit):\n$signOut")
  }
}

tasks.register("buildModule") {
  group = "hollow"
  description = "Compiles an independent feature module against :hollow-sdk only: ./gradlew buildModule -Pmodule=<module_id>"
  dependsOn(":hollow-sdk:bundleLibCompileToJarDebug")
  notCompatibleWithConfigurationCache("Executes kotlinc and d8 for dynamic modules")

  val repoRootDir = rootDir
  val sdkProject = project(":hollow-sdk")

  doLast {
    val targetModule = (project.findProperty("module") as? String)?.trim()
      ?: throw GradleException("Missing module name. Usage: ./gradlew buildModule -Pmodule=<module_id>")
    val modDir = File(repoRootDir, "modules-src/$targetModule")
    if (!modDir.exists() || !modDir.isDirectory) {
      throw GradleException("Module directory not found: modules-src/$targetModule")
    }
    compileAndSignModule(modDir, repoRootDir, sdkProject)
  }
}

tasks.register("buildAllModules") {
  group = "hollow"
  description = "Compiles all independent feature modules against :hollow-sdk only and signs them with Ed25519."
  dependsOn(":hollow-sdk:bundleLibCompileToJarDebug")
  notCompatibleWithConfigurationCache("Executes kotlinc and d8 for dynamic modules")

  val repoRootDir = rootDir
  val sdkProject = project(":hollow-sdk")

  doLast {
    val modulesSrcRoot = File(repoRootDir, "modules-src")
    val moduleDirs = modulesSrcRoot.listFiles()
      ?.filter { it.isDirectory && !it.name.startsWith(".") }
      ?.sortedBy { it.name }
      ?: emptyList()

    for (modDir in moduleDirs) {
      compileAndSignModule(modDir, repoRootDir, sdkProject)
    }
    println("[buildAllModules] Successfully compiled and verified ${moduleDirs.size} standalone modules against :hollow-sdk.")
  }
}

tasks.register("newModule") {
  group = "hollow"
  description = "Scaffolds an independent feature module: ./gradlew newModule -Pid=module_xyz -Pname='My Feature'"
  notCompatibleWithConfigurationCache("Scaffolds module files dynamically")
  val repoRootDir = rootDir
  doLast {
    val modId = project.findProperty("id") as? String
      ?: throw GradleException("Missing module id. Usage: ./gradlew newModule -Pid=module_xyz -Pname='My Feature'")
    val modName = (project.findProperty("name") as? String) ?: modId
    val cleanId = modId.trim().lowercase()

    val targetDir = File(repoRootDir, "modules-src/$cleanId")
    if (targetDir.exists()) {
      throw GradleException("Module directory already exists: modules-src/$cleanId")
    }

    val className = cleanId.split("_")
      .filter { it.isNotBlank() && it != "module" }
      .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Module"
    val subPkg = cleanId.removePrefix("module_").replace("_", "")
    val pkgName = "com.example.isolation.module.$subPkg"

    val srcDir = file("modules-src/$cleanId/src/main/kotlin/" + pkgName.replace(".", "/"))
    srcDir.mkdirs()

    val moduleJson = """
    {
      "moduleId": "$cleanId",
      "name": "$modName",
      "version": 1,
      "minCoreVersion": 1,
      "entryClass": "$pkgName.$className",
      "primaryRoute": "${cleanId.uppercase()}",
      "contributesBottomNavTab": false,
      "features": [
        "$modName capability"
      ],
      "permissions": [],
      "hookTargets": [],
      "accentColorHex": 4279376257
    }
    """.trimIndent()
    file("modules-src/$cleanId/module.json").writeText(moduleJson + "\n")

    val readme = """
    # $modName (`$cleanId`)

    Standalone dynamic security extension module for Hollow Core platform.

    ## Details
    - Entry Class: `$pkgName.$className`
    - Target: `:hollow-sdk` (v1)
    - Zero core dependency: Builds independently and ships as signed DEX.
    """.trimIndent()
    file("modules-src/$cleanId/README.md").writeText(readme + "\n")

    val ktCode = """
    package $pkgName

    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.padding
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.Extension
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.unit.dp
    import com.example.isolation.core.module.HollowModule
    import com.example.isolation.sdk.HollowModuleContext

    object $className : HollowModule {
        override val id: String = "$cleanId"
        override val displayName: String = "$modName"
        override val summary: String = "$modName extension module"
        override val version: Int = 1
        override val requiredMinCoreVersion: Int = 1
        override val icon: ImageVector = Icons.Default.Extension
        override val primaryRoute: String = "${cleanId.uppercase()}"
        override val contributesBottomNavTab: Boolean = false
        override val sizeKb: Int = 256

        override fun onMount(context: HollowModuleContext) {
            context.logger.i("$className", "Mounted $cleanId into Hollow Core")
        }

        override fun onUnmount() {
        }

        @Composable
        override fun RenderPrimaryScreen(
            context: HollowModuleContext,
            onBack: () -> Unit,
            onDetailOpenChanged: (Boolean) -> Unit
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$modName Active")
            }
        }
    }
    """.trimIndent()
    File(srcDir, "$className.kt").writeText(ktCode + "\n")

    println("[newModule] Successfully scaffolded new module in modules-src/$cleanId/")
  }
}
