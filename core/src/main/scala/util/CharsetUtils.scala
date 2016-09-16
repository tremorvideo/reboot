package util

object CharsetUtils {
  def parseCharset(contentType: String): String = {

    var ret: String = null

    contentType.split(";").foreach {
      part =>
        if (part.trim().startsWith("charset=")) {
          val charTup = part.split("=")
          if (charTup(1) != null) {
            ret = charTup(1).trim()
          }
        }
    }

    ret
  }

}
