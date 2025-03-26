package example

import chisel3._
// _root_ disambiguates from package chisel3.util.circt if user imports chisel3.util._
import _root_.circt.stage.ChiselStage

class moduleExample extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(2.W))
    val b = Input(UInt(2.W))
    val out = Output(UInt(2.W))
    val equ = Output(Bool())
  })

  io.out := io.a & io.b
  io.equ := io.a === io.b
  // add printf debug here:
  printf("dut: %d %d => %d\n", io.a, io.b, io.out) // this supports C format
}

/** Generate Verilog sources and save it in file GCD.v
  */
object moduleExample extends App {
  ChiselStage.emitSystemVerilogFile(
    new moduleExample,
    Array("--target-dir", "generated"),
    firtoolOpts = Array("-disable-all-randomization", "-strip-debug-info")
  )
}
