plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hollow.sdk"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
}

abstract class BuildHollowModulesTask : DefaultTask() {
    @get:Internal
    abstract val repoRootProp: Property<File>

    @get:Internal
    abstract val sdkClassesJarProp: Property<File>

    @get:Internal
    abstract val sdkCompileClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val kotlinCompilerClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val composePluginClasspath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val targetModuleProp: Property<String>

    private fun compileAndSignSingleModule(
        modDir: File,
        repoRootDir: File,
        sdkClassesJar: File,
        sdkCompileCp: Set<File>,
        compilerCp: Set<File>,
        pluginCp: Set<File>
    ) {
        val modId = modDir.name
        val ktFiles = modDir.walkTopDown().filter { it.isFile && it.extension == "kt" && !it.path.contains("/build/") }.toList()
        if (ktFiles.isEmpty()) return

        val modBuildDir = File(modDir, "build")
        val classesDir = File(modBuildDir, "classes")
        if (classesDir.exists()) classesDir.deleteRecursively()
        classesDir.mkdirs()

        val sdkRoot = System.getenv("ANDROID_SDK_ROOT")
            ?: System.getenv("ANDROID_HOME")
            ?: "/opt/android/sdk"
        val buildToolsDir = File(sdkRoot, "build-tools")
        val d8Bin = File(sdkRoot, "build-tools/36.0.0/d8").takeIf { it.exists() }
            ?: buildToolsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.map { File(it, "d8") }
                ?.firstOrNull { it.exists() }
            ?: throw GradleException("d8 binary not found under $buildToolsDir")

        val platformsDir = File(sdkRoot, "platforms")
        val androidJar = File(sdkRoot, "platforms/android-36/android.jar").takeIf { it.exists() }
            ?: platformsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.map { File(it, "android.jar") }
                ?.firstOrNull { it.exists() }
            ?: throw GradleException("android.jar not found under $platformsDir")

        val javaHome = System.getProperty("java.home")
        val javaBin = File(javaHome, "bin/java")
        val jarBin = File(javaHome, "bin/jar")

        val compileClasspath = (listOf(androidJar, sdkClassesJar) + sdkCompileCp)
            .filter { it.exists() }
            .joinToString(File.pathSeparator) { it.absolutePath }
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

    @TaskAction
    fun executeBuild() {
        val repoRootDir = repoRootProp.get()
        val sdkClassesJar = sdkClassesJarProp.get()
        val sdkCompileCp = sdkCompileClasspath.files
        val compilerCp = kotlinCompilerClasspath.files
        val pluginCp = composePluginClasspath.files
        val singleModule = targetModuleProp.orNull?.trim().orEmpty()

        if (singleModule.isNotEmpty()) {
            val modDir = File(repoRootDir, "modules-src/$singleModule")
            if (!modDir.exists() || !modDir.isDirectory) {
                throw GradleException("Module directory not found: modules-src/$singleModule")
            }
            compileAndSignSingleModule(modDir, repoRootDir, sdkClassesJar, sdkCompileCp, compilerCp, pluginCp)
        } else {
            val modulesSrcRoot = File(repoRootDir, "modules-src")
            val moduleDirs = modulesSrcRoot.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name }
                ?: emptyList()

            for (modDir in moduleDirs) {
                compileAndSignSingleModule(modDir, repoRootDir, sdkClassesJar, sdkCompileCp, compilerCp, pluginCp)
            }
            println("[buildAllModules] Successfully compiled and verified ${moduleDirs.size} standalone modules against :hollow-sdk.")
        }
    }
}

fun configureSdkModuleBuildTask(task: BuildHollowModulesTask, requireSingleModule: Boolean) {
    val kotlinCompileProvider = tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileDebugKotlin")
    task.dependsOn("bundleLibCompileToJarDebug", kotlinCompileProvider)
    task.repoRootProp.set(rootDir)
    task.sdkClassesJarProp.set(
        layout.buildDirectory.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").map { it.asFile }
    )
    task.sdkCompileClasspath.from(kotlinCompileProvider.map { it.libraries })
    task.kotlinCompilerClasspath.from(configurations.named("kotlinCompilerClasspath"))
    task.composePluginClasspath.from(kotlinCompileProvider.map { it.pluginClasspath })
    if (requireSingleModule) {
        task.targetModuleProp.set(providers.gradleProperty("module"))
    }
}

tasks.register<BuildHollowModulesTask>("buildModule") {
    group = "hollow"
    description = "Compiles an independent feature module against :hollow-sdk only: ./gradlew buildModule -Pmodule=<module_id>"
    configureSdkModuleBuildTask(this, requireSingleModule = true)
}

tasks.register<BuildHollowModulesTask>("buildAllModules") {
    group = "hollow"
    description = "Compiles all independent feature modules against :hollow-sdk only and signs them with Ed25519."
    configureSdkModuleBuildTask(this, requireSingleModule = false)
}

