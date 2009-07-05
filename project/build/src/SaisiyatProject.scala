import sbt._

class SaisiyatProject(info: ProjectInfo) extends AndroidProject(info) {

  override def androidSdkPath = Path.fromFile(new java.io.File("/Users/walter/Personal/lib/java/google/android/android-sdk-mac_x86-1.5_r1"))
}
