package tool

import io.netty.buffer.{ByteBuf, UnpooledByteBufAllocator}

object Uint8Array {
  def from(d: Array[Int]): Uint8Array = {
    val data = new Uint8Array(d.length)
    d.foreach(item => data.setUint8(item))
    data
  }
}

class Uint8Array {
  var size : Int = 0

  def this(size : Int) {
    this
    this.size = size
  }

  def this(b : ByteBuf, size : Int) {
    this
    this.size = size
    buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(size)
    b.readBytes(buffer, size)
  }

  def set(n: Uint8Array) = {
    buffer.writeBytes(n.buffer.copy())
  }


  def setUint8(value: Int): Unit = {
    buffer.writeByte(value & 0xFF)
  }

  def setUint16(value: Int, littleEndian: Boolean): Unit = {
    littleEndian match {
      case true =>  buffer.writeShortLE(value & 0xFFFF)
      case _ => buffer.writeShort(value & 0xFFFF)
    }
  }

  def length = {
    buffer.readableBytes()
  }


  def setUint32(value: Long, littleEndian: Boolean = false): Unit = {
    littleEndian match {
      case true =>  buffer.writeIntLE((value & 0xFFFFFFFF).toInt)
      case _ => buffer.writeInt((value & 0xFFFFFFFF).toInt)
    }
  }

  def setInt16(value: Int, littleEndian: Boolean): Unit = {
    littleEndian match {
      case true =>  buffer.writeShortLE(value)
      case _ => buffer.writeShort(value)
    }
  }


  def setInt32(value: Int, littleEndian: Boolean = false): Unit = {
    littleEndian match {
      case true =>  buffer.writeIntLE(value)
      case _ => buffer.writeInt(value)
    }
  }

  var buffer = UnpooledByteBufAllocator.DEFAULT.heapBuffer(size)
}