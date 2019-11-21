import java.io.UnsupportedEncodingException
import java.net.{URI, URLDecoder}
import java.nio.ByteBuffer
import java.util.concurrent.{Executors, TimeUnit}

import io.netty.buffer.{ByteBuf, UnpooledByteBufAllocator}
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClientOptions, HttpClientResponse, RequestOptions}
import org.java_websocket.drafts.Draft_17
import org.java_websocket.handshake.ServerHandshake
import tool.{SimpleWss, Uint8Array}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object Bilibili {

  //  房间号
  var rid = "7685334"
  var uid = "0"
  var protover = "2"
  var aid = "0"
  var from = "-1"

  val vertx = Vertx.vertx()
  val httpClient = vertx.createHttpClient()

  class HostServer {
    var host : String = ""
    var port : Int = -1
    var wssPort : Int = -1
    var wsPort : Int = -1
  }

  class State {
    var host : String = ""
    var port : Int = -1
    var token : String = ""
    var hostServerList : ArrayBuffer[HostServer] = new ArrayBuffer[HostServer]()
  }

  val stats = new State

  val headers = Map[String, String](
    "User-Agent" -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36",
    "Origin" -> "https://live.bilibili.com")

  def getConf(roomId : String, run : HostServer => Unit) : Unit = {



    val socket = vertx.createHttpClient(HttpClientOptions().setSsl(true)
      .setVerifyHost(false)
      .setTrustAll(true)
      .setKeepAlive(true)
      .setConnectTimeout(5000)
      .setIdleTimeout(10)
      .setMaxWaitQueueSize(10))


    var host = "api.live.bilibili.com"
    var danmakuHosts = s"https://$host/room/v1/Danmu/getConf"
    val option = RequestOptions()
    option.addHeader("Origin", "https://live.bilibili.com")
      .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.90 Safari/537.36")
      //   .addHeader("Accept-Encoding", "gzip, deflate, br")
      //  .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
      .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
      .setHost(host)
      .setPort(443)
      .setSsl(true)
      .setURI(s"/room/v1/Danmu/getConf?room_id=$roomId&platform=pc&player=web")
    val d= socket.getNow(option, new Handler[HttpClientResponse] {
      override def handle(r: HttpClientResponse) = {
        r.bodyHandler((buffer : Buffer) => {
          val resJson = buffer.toJsonObject.getJsonObject("data")
          stats.host = resJson.getString("host")
          stats.port = resJson.getInteger("port")
          stats.token = resJson.getString("token")
          if (resJson.containsKey("host_server_list")) {
            resJson.getJsonArray("host_server_list").forEach { item =>
              val server = new HostServer
              val oneHostServer = item.asInstanceOf[JsonObject]
              server.host = oneHostServer.getString("host")
              server.port= oneHostServer.getInteger("port")
              server.wsPort = oneHostServer.getInteger("wsPort")
              server.wssPort = oneHostServer.getInteger("wssPort")
              stats.hostServerList.append(server)
            }
            run(stats.hostServerList(0))
          }
        })
      }
    })


  }



  /*
  {
                    version: "1.8.5",
                    gitHash: "9cbab11c",
                    build: "257",
                    bundleType: "release"
                }
   */

  def  customAuthParam: Array[Map[String, String]] = {
    Array(
      Map[String, String](
      "type" -> "string",
      "key" -> "platform",
      "value" -> "web"
    ),
      Map[String, String](
      "type" -> "string",
      "key" -> "clientver",
      "value" -> "1.8.5"
    ),
      Map[String, String](
      "type" -> "number",
      "key" -> "type",
      "value" -> "2"
    ),
      Map[String, String](
      "type" -> "string",
      "key" -> "key",
      "value" -> stats.token
    ))
  }

  def main(args: Array[String]): Unit = {

    // https://live.bilibili.com/556288?visit_id=7pny4gau5so0
    Bilibili.rid = "556288"

    val protoLink = new ProtoLink1

    val scheduler = Executors.newScheduledThreadPool(10)

    scheduler.scheduleAtFixedRate(() => {
      protoLink.heartBeat()
    }, 1000 * 20, 30 * 1000, TimeUnit.MILLISECONDS)
    getConf(rid, conf => {
      val wssUrl = s"wss://${conf.host}/sub"
      println(s"websocket url=$wssUrl")
      protoLink.connect(wssUrl, headers)
    })

  }
}


class Bilibili {

}

class ProtoLink1 {
  def close(): Unit = {
    if (this.ws != null) {
      this.ws.close()
    }
  }

  def send(e: Uint8Array): Unit = {
    if (this.ws != null && this.connected == 1) {
      val data = new Array[Byte](e.buffer.readableBytes())
      e.buffer.readBytes(data, e.buffer.readerIndex(), e.buffer.readableBytes())
      ws.send(data)
    }
  }


  def reset(): Unit = {
    if (ws != null) {
      ws.close()
    }
  }

  import org.json4s._
  import org.json4s.native.Serialization
  import org.json4s.native.Serialization._
  implicit val formats = Serialization.formats(NoTypeHints)

  var authInfo = mutable.HashMap[String, Any]()
  authInfo.put("origin", "")
  authInfo.put("encode", "")

  def userAuthentication() : Unit = {
    val n = Bilibili
    var r = Map[String, Any](
      "uid" -> Integer.parseInt(n.uid, 10),
      "roomid" -> Integer.parseInt(n.rid, 10),
      "protover" -> Integer.parseInt(n.protover, 10)
    )
    if (n.aid.toInt > 0){
      r += ("aid" -> Integer.parseInt(n.aid, 10))
    }
    if (n.from.toInt > 0){
      val dd = if (Integer.parseInt(n.from, 10) == 0)  Integer.parseInt(n.from, 10) else 7
      r += ("from" -> dd)
    }

    for (a <- n.customAuthParam.indices) {
      var o = n.customAuthParam(a)
      var s = if (o.contains("type")) o("type") else "string"
      if (r.contains(o("key"))) {
        println("Token has the same key already! 【" + o("key") + "】")
      }
      if (o("key").trim.isEmpty || o("value").trim.isEmpty){
        println("Invalid customAuthParam, missing key or value! 【" + o("key") + "】-【" + o("value") + "】")
      }
      val key = o("key")
      val value = o("value")
      val r1 = s match {
        case "string" => r += (key -> value);true
        case "number" => r += (key -> Integer.parseInt(value, 10));true
        case "boolean" => r += (key -> (value.equalsIgnoreCase("true") || value.equals("1")));true
        case _ => false
      }
      if (!r1) {
        println("Unsupported customAuthParam type!【" + s + "】")
        return
      }
    }
    // "{"uid":0,"roomid":34348,"protover":2,"platform":"web","clientver":"1.8.5","type":2,"key":"Oyl4I7pa7GEzATciHFD1GodWCn0zxf0NQuYw8beBZ8AGNQUdlybHS4cXTdrvU1LiLvSVGTy9fhvjiVoxoWCJj3X4UnMKpBbsm71P5QbE_O7GLuL5gQ=="}"
    val e = this.convertToArrayBuffer(write(r), WS_OP_USER_AUTHENTICATION)
    this.authInfo.put("origin", r)
    this.authInfo.put("encode", e)
    val data = new Array[Byte](e.readableBytes())
    e.readBytes(data, e.readerIndex(), e.readableBytes())
    ws.send(data)
    //      setTimeout(function() {
    //        t.ws.send(e)
    //      }, 0)

  }


  def getAuthInfo() : mutable.HashMap[String, Any] = {
    this.authInfo
  }


  object Encoder {
    def encode(e : String) : ByteBuf = {
      var t = new ArrayBuffer[Byte](e.length)
      var o = e.length
      for (n <- 0 until o) {
        t.append(e.charAt(n).toByte)
      }
      val buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(t.size)
      t.foreach(buffer.writeByte(_))
      buffer
    }
  }

  def decodeURIComponent(s: String): String = {
    if (s == null) return ""
    var result = ""
    try {
      result = URLDecoder.decode(s, "UTF-8")
    }
    catch {
      case e: UnsupportedEncodingException =>
        result = s
    }
    result
  }
  object Decoder {
    def decode(e : ByteBuf) : String = {
      val bytes = new Array[Byte](e.readableBytes())
      e.readBytes(bytes, 0, e.readableBytes())
      decodeURIComponent(new String(bytes))
    }
  }

  class BinaryHeader {

    var name : String = ""
    var key : String = ""
    var bytes : Int = 0
    var offset : Int = 0
    var value : Int = 0

    def this(name : String, key : String, bytes : Int, offset : Int, value : Int) {
      this
      this.name = name
      this.key = key
      this.bytes = bytes
      this.offset = offset
      this.value = value
    }
  }

  object BinaryHeader {

    def apply(name : String, key : String, bytes : Int, offset : Int, value : Int) : BinaryHeader
    = new BinaryHeader(name, key, bytes, offset, value)
  }
  
  val WS_PACKAGE_HEADER_TOTAL_LENGTH = 16
  val WS_PACKAGE_OFFSET = 0
  val WS_HEADER_OFFSET = 4


  val WS_OP_HEARTBEAT = 2
  val WS_OP_HEARTBEAT_REPLY = 3
  val WS_OP_MESSAGE = 5
  val WS_OP_USER_AUTHENTICATION = 7
  val WS_OP_CONNECT_SUCCESS = 8
  val WS_VERSION_OFFSET = 6
  val WS_OPERATION_OFFSET = 8
  val WS_SEQUENCE_OFFSET = 12
  val WS_BODY_PROTOCOL_VERSION_NORMAL = 0
  val WS_BODY_PROTOCOL_VERSION_DEFLATE = 2
  val WS_HEADER_DEFAULT_VERSION = 1
  val WS_HEADER_DEFAULT_OPERATION = 1
  val WS_HEADER_DEFAULT_SEQUENCE = 1

  val wsBinaryHeaderList = Array(
    BinaryHeader(
    name = "Header Length",
    key = "headerLen",
    bytes = 2,
    offset = WS_HEADER_OFFSET,
    value = WS_PACKAGE_HEADER_TOTAL_LENGTH
  ), BinaryHeader(
    name = "Protocol Version",
    key = "ver",
    bytes = 2,
    offset = WS_VERSION_OFFSET,
    value = WS_HEADER_DEFAULT_VERSION
  ), BinaryHeader(
    name = "Operation",
    key = "op",
    bytes = 4,
    offset = WS_OPERATION_OFFSET,
    value = WS_HEADER_DEFAULT_OPERATION
  ), BinaryHeader(
    name = "Sequence Id",
    key = "seq",
    bytes = 4,
    offset = WS_SEQUENCE_OFFSET,
    value = WS_HEADER_DEFAULT_SEQUENCE
  )
  )

  // "{"uid":0,"roomid":34348,"protover":2,"platform":"web","clientver":"1.8.5","type":2,"key":"xSX4CnfdNO7sRzLt0UtAhbCermIFf4OFyc9TeFlQgwn_Y-Rl7cd4PUGpMb8QyL-DwVvIEmJAhXKfFH2pYYbuyMA9HSQ_52yBXD7gOJNATxKe_Z3EPw=="}"
  // Uint8Array(208) [123, 34, 117, 105, 100, 34, 58, 48, 44, 34, 114, 111, 111, 109, 105, 100, 34, 58, 51, 52, 51, 52, 56, 44, 34, 112, 114, 111, 116, 111, 118, 101, 114, 34, 58, 50, 44, 34, 112, 108, 97, 116, 102, 111, 114, 109, 34, 58, 34, 119, 101, 98, 34, 44, 34, 99, 108, 105, 101, 110, 116, 118, 101, 114, 34, 58, 34, 49, 46, 56, 46, 53, 34, 44, 34, 116, 121, 112, 101, 34, 58, 50, 44, 34, 107, 101, 121, 34, 58, 34, 120, 83, 88, 52, 67, 110, 102, 100, 78, 79, …]
  def convertToArrayBuffer(e : String, t : Int) : ByteBuf = {
    var n = UnpooledByteBufAllocator.DEFAULT.heapBuffer(WS_PACKAGE_HEADER_TOTAL_LENGTH)
    val r = n.writerIndex(WS_PACKAGE_HEADER_TOTAL_LENGTH) // n.writerIndex(WS_PACKAGE_OFFSET)
    val o = Encoder.encode(e)
    r.setInt(WS_PACKAGE_OFFSET, WS_PACKAGE_HEADER_TOTAL_LENGTH + o.readableBytes())
    this.wsBinaryHeaderList(2).value = t
    this.wsBinaryHeaderList.foreach { e =>
      if (4 == e.bytes) r.setInt(e.offset, e.value) else if (2 == e.bytes) r.setShort(e.offset, e.value)
    }
    mergeArrayBuffer(n, o)
  }


  def mergeArrayBuffer(e : ByteBuf , t : ByteBuf) : ByteBuf = {
    val merge =UnpooledByteBufAllocator.DEFAULT.heapBuffer(e.readableBytes() + t.readableBytes())
    merge.writeBytes(e)
    merge.writeBytes(t);
    merge
  }


  def handleMessage(data: ByteBuffer) : Unit = {
    try {
      val t = UnpooledByteBufAllocator.DEFAULT.heapBuffer(data.limit())
      t.writeBytes(data)
      var n = this.convertToObject(t)

      /*
      if (n.isInstanceOf[Array]){
        n.foreach { e =>
          t.onMessage(e)
        }
      }
      else if (n instanceof Object) */
      n("op").asInstanceOf[Int] match {
        case WS_OP_HEARTBEAT_REPLY => this.onHeartBeatReply(n("body"))
        case WS_OP_MESSAGE => this.onMessageReply(n("body"))
        case WS_OP_CONNECT_SUCCESS => this.heartBeat()
      }
    } catch {
      case e : Throwable => println("WebSocket Error: ", e)
    }
  }


  def onHeartBeatReply(e : Any) : Unit = {
    println("onHeartBeatReply...")
  }



  def onMessageReply(e : Any) : Unit = {

    try {
      e match {
        case x : ArrayBuffer[Any] => x.foreach { e1 =>
          onMessageReply(e1)
        }
        case m : Map[String, Any] => onReceivedMessage(m)
        case _ => println(s"unknow :$e")
      }
    } catch {
      case ex : Throwable =>  println("On Message Resolve Error: ", ex);ex.printStackTrace()
    }
  }

  def onReceivedMessage(m: Map[String, Any]): Unit = {
    println(m)
  }


  def heartBeat() : Unit = {
    println("send heartbeating.............")
    var t = this.convertToArrayBuffer("{}", WS_OP_HEARTBEAT)
    val data = new Array[Byte](t.readableBytes())
    t.readBytes(data, t.readerIndex(), t.readableBytes())
    this.ws.send(data)
  }


  def convertToObject(e : ByteBuf) : mutable.HashMap[String, Any] = {
    val t = e
    val n = mutable.HashMap[String, Any]()
    n.put("body", new ArrayBuffer[Any]())
    val packetLen = t.getInt(WS_PACKAGE_OFFSET)
    n.put("packetLen", packetLen)
    this.wsBinaryHeaderList.foreach { e1 =>
      e1.bytes match {
        case 4 => n.put(e1.key, t.getInt(e1.offset))
        case 2 => n.put(e1.key, t.getShort(e1.offset))
      }
    }

    if (packetLen < t.readableBytes()) {
      this.convertToObject(t.slice(0, packetLen))
    }

    if (n.contains("op") && n("op").asInstanceOf[Number].intValue() == WS_OP_MESSAGE) {
      var s = packetLen
      var l = 0
      var u : Any = null
      var r = WS_PACKAGE_OFFSET
      while (r < e.readableBytes()) {
        s = t.getInt(r)
        l = t.getShort(r + WS_HEADER_OFFSET)
        try {
          if (n("ver").asInstanceOf[Number].intValue() == WS_BODY_PROTOCOL_VERSION_DEFLATE) {
            var c = e.slice(r + l, s - l)
            val d1 = new Array[Byte](c.readableBytes())
            c.readBytes(d1, 0, c.readableBytes())
            var d = zlib.decompress(d1)
            val buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(d.length)
            buffer.writeBytes(d)
            u = this.convertToObject(buffer)("body").asInstanceOf[ArrayBuffer[Any]]
          } else {
            u = write(Decoder.decode(e.slice(r + l, s - l)))
          }
          if (u != null) {
            n("body").asInstanceOf[ArrayBuffer[Any]].append(u)
          }
        } catch {
          case t : Throwable => println("decode body error:", e, n, t);t.printStackTrace()
        }
        r += s
      }
    } else {
      if (n.contains("op") && WS_OP_HEARTBEAT_REPLY == n("op").asInstanceOf[Int]) {
        n.put("body", Map("count" -> t.getInt(WS_PACKAGE_HEADER_TOTAL_LENGTH)))
      }
    }
    n
  }
  var ws : SimpleWss = _
  var connected = 0

  def connect(t : String, headers : Map[String, String] = Map[String, String]()) : Unit = {
    if (ws == null) {
      println("ProtoLink.connect addr=" + t)
      import scala.collection.JavaConverters._
     // WebSocketImpl.DEBUG = true


      ws = new SimpleWss(new URI(t), new Draft_17(), headers.asJava) {

        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
          connected = 0
          ws = null
        }

        override def onMessage(message: String): Unit = {
          println(s"text message=$message")
        }

        override def onMessage(data: ByteBuffer): Unit = {
          handleMessage(data)
        }

        override def onOpen(handshakedata: ServerHandshake): Unit = {
          userAuthentication()
          connected = 1
          println("onOpen!")
        }

        override def onError(ex: Exception): Unit = {
          ex.printStackTrace()
          connected = 0
          ws = null
        }
      }
      ws.connect()
    }
  }
}