package example

import chisel3._
import chisel3.util._

class FifoIO[T <: Data](private val gen: T) extends Bundle {
  val enq = Flipped(new DecoupledIO(gen))
  val deq = new DecoupledIO(gen)
}

abstract class Fifo[T <: Data](gen: T, val depth: Int) extends Module {
  val io = IO(new FifoIO(gen))
  assert(depth > 0, "Number of buffer elements needs to be larger than 0")
}

/* fifo with only one reg */
class FifoRegister[T <: Data](gen: T) extends Fifo(gen: T, 1) {
  object State extends ChiselEnum { // a FSM
    val empty, full = Value
  }
  import State._

  val stateReg = RegInit(empty)
  val dataReg = RegInit(gen)

  // connect output
  io.enq.ready := stateReg === empty
  io.deq.valid := stateReg === full
  io.deq.bits := dataReg

  when(stateReg === empty) {
    when(io.enq.valid) {
      dataReg := io.enq.bits
      stateReg := full
    }
  }.otherwise {
    when(io.deq.ready) {
      stateReg := empty
    }
  }
}

/* combine multiple fifo registers to form a fifo */
class BufferForTx[T <: Data](gen: T, depth: Int)
    extends Fifo(gen: T, depth: Int) {
  // create an array of Fifo registers
  private val buffers = Array.fill(depth) { Module(new FifoRegister(gen)) }
  // connect them with a for loop
  for (i <- 0 until depth - 1) {
    buffers(i).io.deq <> buffers(i + 1).io.enq
  }
  io.enq <> buffers(0).io.deq
  io.deq <> buffers(depth - 1).io.deq
}
