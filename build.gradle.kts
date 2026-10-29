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
        violations.add("Forbidden feature screen found in core: ${file.relativeTo(repoRootDir)}")
      }
      if (forbiddenHookNames.contains(baseName)) {
        violations.add("Forbidden feature hook found in core: ${file.relativeTo(repoRootDir)}")
      }
      for (forbidden in forbiddenIds) {
        if (content.contains(forbidden)) {
          violations.add("Hardcoded module ID reference $forbidden found in ${file.relativeTo(repoRootDir)}")
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
tasks.register("buildModule") {
  group = "hollow"
  description = "Compiles an independent feature module against :hollow-sdk only: ./gradlew buildModule -Pmodule=<module_id>"
  dependsOn(":hollow-sdk:buildModule")
}

tasks.register("buildAllModules") {
  group = "hollow"
  description = "Compiles all independent feature modules against :hollow-sdk only and signs them with Ed25519."
  dependsOn(":hollow-sdk:buildAllModules")
}

tasks.register("compileFeatureModules") {
  group = "hollow"
  description = "Compatibility alias for buildAllModules."
  dependsOn(":hollow-sdk:buildAllModules")
}

tasks.register("newModule") {
  group = "hollow"
  description = "Scaffolds an independent feature module: ./gradlew newModule -Pid=module_xyz -Pname='My Feature'"
  val repoRootDir = rootDir
  val idProp = providers.gradleProperty("id")
  val nameProp = providers.gradleProperty("name")
  doLast {
    val modId = idProp.orNull
      ?: throw GradleException("Missing module id. Usage: ./gradlew newModule -Pid=module_xyz -Pname='My Feature'")
    val modName = nameProp.orNull ?: modId
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

    val srcDir = File(repoRootDir, "modules-src/$cleanId/src/main/kotlin/" + pkgName.replace(".", "/"))
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
    File(repoRootDir, "modules-src/$cleanId/module.json").writeText(moduleJson + "\n")

    val readme = """
    # $modName (`$cleanId`)

    Standalone dynamic security extension module for Hollow Core platform.

    ## Details
    - Entry Class: `$pkgName.$className`
    - Target: `:hollow-sdk` (v1)
    - Zero core dependency: Builds independently and ships as signed DEX.
    """.trimIndent()
    File(repoRootDir, "modules-src/$cleanId/README.md").writeText(readme + "\n")

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
