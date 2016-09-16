package dispatch.as

import dispatch.OkHandler
import org.asynchttpclient.Response
import java.nio.charset.Charset

import org.asynchttpclient.handler.resumable.{ResumableRandomAccessFileListener, ResumableAsyncHandler}

object Response {
  def apply[T](f: Response => T) = f
}

object String extends (Response => String) {
  /** @return response body as a string decoded as either the charset provided by
   *  Content-Type header of the response or ISO-8859-1 */
  def apply(r: Response) = r.getResponseBody

  /** @return a function that will return response body decoded in the provided charset */
  case class charset(set: Charset) extends (Response => String) {
    def apply(r: Response) = r.getResponseBody(set)
  }

  /** @return a function that will return response body as a utf8 decoded string */
  object utf8 extends charset(Charset.forName("utf8"))
}

object Bytes extends (Response => Array[Byte]) {
  def apply(r: Response) = r.getResponseBodyAsBytes
}

object File extends {
  def apply(file: java.io.File) =
    new ResumableAsyncHandler()
      .setResumableListener(
        new ResumableRandomAccessFileListener(
          new java.io.RandomAccessFile(file, "rw")
        )
      )
}
