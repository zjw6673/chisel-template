/* this is a test example using chiseltest*/

package example

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec
import example.moduleExample

class moduleExampleTest extends AnyFlatSpec with ChiselScalatestTester {
  "DUT" should "pass" in {
    test(new moduleExample) { dut =>
      dut.io.a.poke(0.U) // set input via poke
      dut.io.b.poke(1.U)
      dut.clock.step() // advance simulation by one clock cycle
      println(
        "Result is: " + dut.io.out.peekInt()
      ) // read output via peekInt() | peekBoolean() ...
      dut.io.out.expect(0.U) // use .expect to auto eval

      dut.io.a.poke(3.U)
      dut.io.b.poke(2.U)
      dut.clock.step()
      println("Result is: " + dut.io.out.peekInt())
      dut.io.out.expect(2.U)
      // or you can use assert
      val equ = dut.io.equ.peekBoolean()
      assert(!equ)

      // use scala grammar to scale up your test
      for (a <- 0 until 4) {
        for (b <- 0 until 4) {
          dut.io.a.poke(a.U)
          dut.io.b.poke(b.U)
          dut.clock.step()
        }
      }
    }
  }
}
