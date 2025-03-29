package example

import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

// use a valid/ready handshake
class UartIO extends DecoupledIO(UInt(8.W)) {}

// Tx means Transmitter: transmit a byte into serial signals
class Tx(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    // the port to send serial data
    val txd = Output(UInt(1.W))
    // the port to receive data to send
    val channel = Flipped(new UartIO()) // use Flipped to set read to output
  })
  // compute send signal interval
  val BIT_CNT = ((frequency + baudRate / 2) / baudRate - 1).asUInt
  // create hardwares
  val shiftReg = RegInit(0x7ff.U) // 11 bits width: 0+byts+11
  val cntReg = RegInit(0.U(20.W))
  val bitsReg = RegInit(0.U(4.W))
  // connect to io output
  io.channel.ready := (cntReg === 0.U) && (bitsReg === 0.U)
  io.txd := shiftReg(0)
  when(cntReg === 0.U) { // at each interval
    cntReg := BIT_CNT
    when(bitsReg =/= 0.U) { // still have data to send
      val shift = shiftReg >> 1
      shiftReg := 1.U ## shift(9, 0)
      bitsReg := bitsReg - 1.U
    }.otherwise { // full data seiries has been sent
      when(io.channel.valid) { // ready to read data
        shiftReg := 3.U ## io.channel.bits ## 0.U
        bitsReg := 11.U
      }.otherwise {
        shiftReg := 0x7ff.U
      }
    }
  }.otherwise {
    cntReg := cntReg - 1.U
  }
}

// Tx with a 4-depth bubble buffer
class TxWithBuffer(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd = Output(UInt(1.W))
    val channel = Flipped(new UartIO)
  })
  val tx = Module(new Tx(frequency, baudRate))
  val buf = Module(new BufferForTx(8, 4))

  io.channel <> buf.io.in
  buf.io.out <> tx.io.channel
  tx.io.txd <> io.txd
}

// Rx means receiver: receive a serial signal and transfer in to byte
class Rx(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val rxd = Input(UInt(1.W))
    val channel = new UartIO()
  })
  val BIT_CNT = ((frequency + baudRate / 2) / baudRate - 1)
  val START_CNT = ((3 * frequency / 2 + baudRate / 2) / baudRate - 2)

  // Sync in the asychronous Rx data
  val rxReg = RegNext(RegNext(io.rxd, 0.U), 0.U)
  val falling = !rxReg && (RegNext(rxReg) === 1.U)

  // central hardware
  val shiftReg = RegInit(0.U(8.W))
  val cntReg = RegInit(BIT_CNT.U(20.W))
  val bitsReg = RegInit(0.U(4.W))
  val valReg = RegInit(false.B)

  when(cntReg =/= 0.U) {
    cntReg := cntReg - 1.U
  }.elsewhen(bitsReg =/= 0.U) { // not idling
    cntReg := BIT_CNT.U
    shiftReg := Cat(rxReg, shiftReg >> 1)
    bitsReg := bitsReg - 1.U
    when(bitsReg === 1.U) {
      valReg := true.B
    }
  }.elsewhen(falling) { // idling and received a start bit
    cntReg := START_CNT.U
    bitsReg := 8.U
  }
  when(valReg && io.channel.ready) {
    valReg := false.B
  }
  io.channel.bits := shiftReg
  io.channel.valid := valReg
}

class Sender(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd = Output(UInt(1.W))
  })
  val tx = Module(new TxWithBuffer(frequency, baudRate))
  io.txd := tx.io.txd

  val msg = "Hello World!"
  val text = VecInit(msg.map(_.U))
  val len = msg.length.U

  val cntReg = RegInit(0.U(8.W))

  tx.io.channel.bits := text(cntReg)
  tx.io.channel.valid := cntReg =/= len

  when(tx.io.channel.ready && cntReg =/= len) {
    cntReg := cntReg + 1.U
  }
}

class Echo(frequency: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd = Output(UInt(1.W))
    val rxd = Input(UInt(1.W))
  })
  val tx = Module(new TxWithBuffer(frequency, baudRate))
  val rx = Module(new Rx(frequency, baudRate))
  io.txd := tx.io.txd
  rx.io.rxd := io.rxd
  tx.io.channel <> rx.io.channel
}

/** Generate Verilog sources and save it in file GCD.v
  */
object Uart extends App {
  ChiselStage.emitSystemVerilogFile(
    new Echo(1000, 200),
    Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
