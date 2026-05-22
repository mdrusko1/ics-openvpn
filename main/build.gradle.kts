import java.io.File
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskProvider

/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    alias(libs.plugins.android.library)
    id("checkstyle")
}


fun obtainTestBuildType(): String {
    var result = "debug";

    if (project.hasProperty("testBuildType")) {
        result = project.property("testBuildType").toString()
    }
    return result
}

android {
    buildFeatures {
        aidl = true
        buildConfig = true
    }
    namespace = "de.blinkt.openvpn"
    compileSdk = 36
    //compileSdkPreview = "UpsideDownCake"

    // Also update runcoverity.sh
    ndkVersion = "30.0.14904198"

    defaultConfig {
        minSdk = 21
        //targetSdk = 36
        //targetSdkPreview = "UpsideDownCake"
        //versionCode = 219
        //versionName = "0.7.64"
        externalNativeBuild {
            cmake {
                //arguments+= "-DCMAKE_VERBOSE_MAKEFILE=1"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add("build/ovpnassets")
        }

        create("ui") {}

        create("skeleton") {}

        getByName("debug") {}

        getByName("release") {}
    }

    signingConfigs {
        create("release") {
            // ~/.gradle/gradle.properties
            val keystoreFile: String? by project
            storeFile = keystoreFile?.let { file(it) }
            val keystorePassword: String? by project
            storePassword = keystorePassword
            val keystoreAliasPassword: String? by project
            keyPassword = keystoreAliasPassword
            val keystoreAlias: String? by project
            keyAlias = keystoreAlias
            enableV1Signing = true
            enableV2Signing = true
        }

        create("releaseOvpn2") {
            // ~/.gradle/gradle.properties
            val keystoreO2File: String? by project
            storeFile = keystoreO2File?.let { file(it) }
            val keystoreO2Password: String? by project
            storePassword = keystoreO2Password
            val keystoreO2AliasPassword: String? by project
            keyPassword = keystoreO2AliasPassword
            val keystoreO2Alias: String? by project
            keyAlias = keystoreO2Alias
            enableV1Signing = true
            enableV2Signing = true
        }

    }

    lint {
        enable += setOf(
            "BackButton",
            "EasterEgg",
            "StopShip",
            "IconExpectedSize",
            "GradleDynamicVersion",
            "NewerVersionAvailable"
        )
        checkOnly += setOf("ImpliedQuantity", "MissingQuantity")
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }


    flavorDimensions += listOf("implementation", "ovpnimpl")

    productFlavors {
        create("ui") {
            dimension = "implementation"
            externalNativeBuild {
                cmake {
                    arguments("-DUI_BUILD=ON")
                }
            }
        }

        create("skeleton") {
            dimension = "implementation"
            externalNativeBuild {
                cmake {
                    arguments("-DUI_BUILD=OFF")
                }
            }
        }

        create("ovpn23") {
            dimension = "ovpnimpl"
            buildConfigField("boolean", "openvpn3", "true")
            externalNativeBuild {
                cmake {
                    arguments("-DOPENVPN3_BUILD=ON")
                }
            }
        }

        create("ovpn2") {
            dimension = "ovpnimpl"
            //versionNameSuffix = "-o2"
            buildConfigField("boolean", "openvpn3", "false")
            externalNativeBuild {
                cmake {
                    arguments("-DOPENVPN3_BUILD=OFF")
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (project.hasProperty("icsopenvpnDebugSign")) {
                logger.warn("property icsopenvpnDebugSign set, using debug signing for release")
                signingConfig = signingConfigs.getByName("debug")
            } else {
                productFlavors["ovpn23"].signingConfig = signingConfigs.getByName("release")
                productFlavors["ovpn2"].signingConfig = signingConfigs.getByName("releaseOvpn2")
            }
        }
    }

    testBuildType = obtainTestBuildType()

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

fun registerGenSwigTask(variantName: String, variantDirName: String): TaskProvider<GenerateSwigTask> {
    val baseDir = layout.buildDirectory.dir("generated/source/ovpn3swig/${variantDirName}")

    return tasks.register<GenerateSwigTask>("generateOpenVPN3Swig${variantName}") {
        outputDir.set(baseDir)

        val swigExecutable = when {
            File("/opt/homebrew/bin/swig").exists() -> "/opt/homebrew/bin/swig"
            File("/usr/local/bin/swig").exists() -> "/usr/local/bin/swig"
            else -> "swig"
        }
        val genDirAbsolutePath = baseDir.get().asFile.resolve("net/openvpn/ovpn3").absolutePath

        commandLine(
            listOf(
                swigExecutable,
                "-outdir",
                genDirAbsolutePath,
                "-outcurrentdir",
                "-c++",
                "-java",
                "-package",
                "net.openvpn.ovpn3",
                "-Isrc/main/cpp/openvpn3/client",
                "-Isrc/main/cpp/openvpn3/",
                "-DOPENVPN_PLATFORM_ANDROID",
                "-o",
                "${genDirAbsolutePath}/ovpncli_wrap.cxx",
                "-oh",
                "${genDirAbsolutePath}/ovpncli_wrap.h",
                "src/main/cpp/openvpn3/client/ovpncli.i"
            )
        )
        inputs.files("src/main/cpp/openvpn3/client/ovpncli.i")
    }
}

androidComponents {
    onVariants(selector().withFlavor("ovpnimpl" to "ovpn23")) { variant ->
        val execTask = registerGenSwigTask(variant.name, variant.name.replace("-", "/"))
        variant.sources.java?.addGeneratedSourceDirectory(execTask, GenerateSwigTask::outputDir)
    }
}

dependencies {
    // https://maven.google.com/web/index.html
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)

    uiImplementation(libs.android.view.material)
    uiImplementation(libs.androidx.activity)
    uiImplementation(libs.androidx.activity.ktx)
    uiImplementation(libs.androidx.appcompat)
    uiImplementation(libs.androidx.cardview)
    uiImplementation(libs.androidx.viewpager2)
    uiImplementation(libs.androidx.constraintlayout)
    uiImplementation(libs.androidx.core.ktx)
    uiImplementation(libs.androidx.fragment.ktx)
    uiImplementation(libs.androidx.lifecycle.runtime.ktx)
    uiImplementation(libs.androidx.lifecycle.viewmodel.ktx)
    uiImplementation(libs.androidx.preference.ktx)
    uiImplementation(libs.androidx.recyclerview)
    uiImplementation(libs.androidx.security.crypto)
    uiImplementation(libs.androidx.webkit)
    uiImplementation(libs.kotlin)
    uiImplementation(libs.mpandroidchart)
    uiImplementation(libs.square.okhttp)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
}

fun DependencyHandler.uiImplementation(dependencyNotation: Any): Dependency? =
    add("uiImplementation", dependencyNotation)
