import com.netgents.antelese.Antelese.{path => antpath, task => anttask, _}
import io.Source
import java.io._
import sbt._

object AndroidProject {
  val DefaultAaptName = "aapt"
  val DefaultAdbName = "adb"
  val DefaultAidlName = "aidl"
  val DefaultApkbuilderName = "apkbuilder"
  val DefaultDxName = "dx"
  val DefaultAndroidPlatformName = "android-1.5"
  val DefaultAndroidManifestName = "AndroidManifest.xml"
  val DefaultAndroidJarName = "android.jar"
  val DefaultAssetsDirectoryName = "assets"
  val DefaultResDirectoryName = "res"
  val DefaultClassesMinJarName = "classes.min.jar"
  val DefaultClassesDexName = "classes.dex"
  val DefaultResourcesApkName = "resources.apk"
}

abstract class AndroidProject(info: ProjectInfo) extends DefaultProject(info) {

  import AndroidProject._

  def aaptName = DefaultAaptName
  def adbName = DefaultAdbName
  def aidlName = DefaultAidlName
  def apkbuilderName = DefaultApkbuilderName
  def dxName = DefaultDxName
  def androidPlatformName = DefaultAndroidPlatformName
  def androidManifestName = DefaultAndroidManifestName
  def androidJarName = DefaultAndroidJarName
  def assetsDirectoryName = DefaultAssetsDirectoryName
  def resDirectoryName = DefaultResDirectoryName
  def classesMinJarName = DefaultClassesMinJarName
  def classesDexName = DefaultClassesDexName
  def packageApkName = name + ".apk"
  def resourcesApkName = DefaultResourcesApkName

  def androidSdkPath: Path
  def scalaHomePath = Path.fromFile(new File(System.getProperty("scala.home")))
  def androidToolsPath = androidSdkPath / "tools"
  def apkbuilderPath = androidToolsPath / DefaultApkbuilderName
  def adbPath = androidToolsPath / adbName
  def androidPlatformPath = androidSdkPath / "platforms" / androidPlatformName
  def platformToolsPath = androidPlatformPath / "tools"
  def aaptPath = platformToolsPath / aaptName
  def aidlPath = platformToolsPath / aidlName
  def dxPath = platformToolsPath / DefaultDxName

  def androidManifestPath =  mainSourcePath / androidManifestName
  def androidJarPath = androidPlatformPath / androidJarName
  def mainAssetsPath = mainSourcePath / assetsDirectoryName
  def mainResPath = mainSourcePath / resDirectoryName
  def classesMinJarPath = outputPath / classesMinJarName
  def classesDexPath =  outputPath / classesDexName
  def resourcesApkPath = outputPath / resourcesApkName
  def packageApkPath = outputPath / packageApkName

  lazy val aaptGenerate = aaptGenerateAction
  def aaptGenerateAction = aaptGenerateTask describedAs("Generate R.java.")
  def aaptGenerateTask = task {
    exec(
      'executable -> aaptPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> "package"),
      'arg -> ('value -> "-m"),
      'arg -> ('value -> "-M"),
      'arg -> ('value -> androidManifestPath.absolutePath),
      'arg -> ('value -> "-S"),
      'arg -> ('value -> mainResPath.absolutePath),
      'arg -> ('value -> "-I"),
      'arg -> ('value -> androidJarPath.absolutePath),
      'arg -> ('value -> "-J"),
      'arg -> ('value -> mainJavaSourcePath.absolutePath))
    None
  }

  lazy val aidl = aidlAction
  def aidlAction = aidlTask describedAs("Generate Java classes from .aidl files.")
  def aidlTask = task {
    apply(
      'executable -> aidlPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> "-o"),
      'arg -> ('value -> mainJavaSourcePath.absolutePath),
      'fileset -> fileset('dir -> mainScalaSourcePath.absolutePath, 'includes -> "**/*.aidl"),
      'fileset -> fileset('dir -> mainJavaSourcePath.absolutePath, 'includes -> "**/*.aidl"))
    None
  }
  
  override def compileAction = super.compileAction dependsOn(aaptGenerate, aidl)

  lazy val proguard = proguardAction
  def proguardAction = proguardTask dependsOn(compile) describedAs("Optimize class files.")
  def proguardTask = task {
    taskdef('resource -> "proguard/ant/task.properties")
    anttask("proguard")('<> ->
      <a>
      -injars {mainCompilePath.absolutePath}:{(scalaHomePath / "lib" / "scala-library.jar").absolutePath}(!META-INF/MANIFEST.MF,!library.properties)
      -outjars {classesMinJarPath.absolutePath}
      -libraryjars {androidJarPath.absolutePath}
      -dontwarn
      -dontoptimize
      -dontobfuscate
      -keep public class * extends android.app.Activity
      -keep public class * extends android.appwidget.AppWidgetProvider
      </a>.text)          
    None
  }

  lazy val dx = dxAction
  def dxAction = dxTask dependsOn(proguard) describedAs("Convert class files to dex files")
  def dxTask = task {
    apply(
      'executable -> dxPath.absolutePath,
      'failonerror -> true,
      'parallel -> true,
      'arg -> ('value -> "--dex"),
      'arg -> ('value -> ("--output=" + classesDexPath.absolutePath)),
      'fileset -> fileset('file -> classesMinJarPath.absolutePath))
    None
  }

  lazy val aaptPackage = aaptPackageAction
  def aaptPackageAction = aaptPackageTask dependsOn(dx) describedAs("Package resources and assets.")
  def aaptPackageTask = task {
    exec(
      'executable -> aaptPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> "package"),
      'arg -> ('value -> "-f"),
      'arg -> ('value -> "-M"),
      'arg -> ('value -> androidManifestPath.absolutePath),
      'arg -> ('value -> "-S"),
      'arg -> ('value -> mainResPath.absolutePath),
      'arg -> ('value -> "-A"),
      'arg -> ('value -> mainAssetsPath.absolutePath),
      'arg -> ('value -> "-I"),
      'arg -> ('value -> androidJarPath.absolutePath),
      'arg -> ('value -> "-F"),
      'arg -> ('value -> resourcesApkPath.absolutePath))
    None
  }

  lazy val packageDebug = packageDebugAction
  def packageDebugAction = packageTask(true) dependsOn(aaptPackage) describedAs("Package and sign with a debug key.")

  lazy val packageRelease = packageReleaseAction
  def packageReleaseAction = packageTask(false) dependsOn(aaptPackage) describedAs("Package without signing.")

  def packageTask(signPackage: Boolean) = task {
    delete('file -> packageApkPath.absolutePath)
    exec(
      'executable -> apkbuilderPath.absolutePath, 
      'failonerror -> true,
      'arg -> ('value -> packageApkPath.absolutePath),
      if (signPackage) ignore else 'arg -> ('value -> "-u"),
      'arg -> ('value -> "-z"),
      'arg -> ('value -> resourcesApkPath.absolutePath),
      'arg -> ('value -> "-f"),
      'arg -> ('value -> classesDexPath.absolutePath))
    None
  }

  lazy val installEmulator = installEmulatorAction
  def installEmulatorAction = installTask(true) dependsOn(packageDebug) describedAs("Install package on the default emulator.")

  lazy val installDevice = installDeviceAction
  def installDeviceAction = installTask(false) dependsOn(packageDebug) describedAs("Install package on the default device.")

  def installTask(emulator: Boolean) = task {
    exec(
      'executable -> adbPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> (if (emulator) "-e" else "-d")),
      'arg -> ('value -> "install"),
      'arg -> ('value -> packageApkPath.absolutePath))
    None
  }

  lazy val reinstallEmulator = reinstallEmulatorAction
  def reinstallEmulatorAction = reinstallTask(true) dependsOn(packageDebug) describedAs("Reinstall package on the default emulator.")

  lazy val reinstallDevice = reinstallDeviceAction
  def reinstallDeviceAction = reinstallTask(false) dependsOn(packageDebug) describedAs("Reinstall package on the default device.")

  def reinstallTask(emulator: Boolean) = task {
    exec(
      'executable -> adbPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> (if (emulator) "-e" else "-d")),
      'arg -> ('value -> "install"),
      'arg -> ('value -> "-r"),
      'arg -> ('value -> packageApkPath.absolutePath))
    None
  }

  lazy val uninstallEmulator = uninstallEmulatorAction
  def uninstallEmulatorAction = uninstallTask(true) dependsOn(packageDebug) describedAs("Uninstall package on the default emulator.")

  lazy val uninstallDevice = uninstallDeviceAction
  def uninstallDeviceAction = uninstallTask(false) dependsOn(packageDebug) describedAs("Uninstall package on the default device.")

  def uninstallTask(emulator: Boolean) = task {
    exec(
      'executable -> adbPath.absolutePath,
      'failonerror -> true,
      'arg -> ('value -> (if (emulator) "-e" else "-d")),
      'arg -> ('value -> "uninstall"),
      'arg -> ('value -> packageApkPath.absolutePath))
    None
  }
}
