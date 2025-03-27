package templates

import chisel3._
import chisel3.util._

class InputProcess extends Module {
  def sync(v: Bool) = RegNext(RegNext(v)) // synchronize boolean input

  def rising(v: Bool) = v & !RegNext(v)

  def tickGen(cycleLen: Int) = {
    val MAX = (cycleLen - 2).S(log2Up(cycleLen).W)
    val reg = RegInit(MAX)
    reg := Mux(reg(log2Up(cycleLen) - 1), MAX, reg - 1.S)

    val tick = reg(log2Up(cycleLen) - 1)
    tick
  }

  def filter(v: Bool, tick: Bool) = {
    val reg = RegInit(0.U(3.W))
    when(tick) {
      reg := reg(1, 0) ## v
    }
    (reg(2) & reg(1)) | (reg(2) & reg(0)) | (reg(1) & reg(0))
  }
}
