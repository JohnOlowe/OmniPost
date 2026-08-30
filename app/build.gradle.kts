plugins {
  alias(libs.plugins.android.application)
}

android {
  namespace = "damjay.publicity.omnipost"
  compileSdk = 36
  buildToolsVersion = "36.0.0"

  defaultConfig {
    applicationId = "damjay.publicity.omnipost"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("omnipost") {
      storeFile = rootProject.file(property("OMNIPOST_STORE_FILE").toString())
      storePassword = property("OMNIPOST_STORE_PASSWORD").toString()
      keyAlias = property("OMNIPOST_KEY_ALIAS").toString()
      keyPassword = property("OMNIPOST_KEY_PASSWORD").toString()
    }
  }

  buildTypes {
    debug {
      signingConfig = signingConfigs.getByName("omnipost")
    }
    release {
      signingConfig = signingConfigs.getByName("omnipost")
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  buildFeatures {
    viewBinding = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.all {
      it.testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  testLogging {
    events("passed", "skipped", "failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}

dependencies {
  implementation(libs.androidx.core)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.coordinatorlayout)
  implementation(libs.androidx.lifecycle.livedata)
  implementation(libs.androidx.room.runtime)
  annotationProcessor(libs.androidx.room.compiler)
  testImplementation(libs.junit)
}
