Chisel Project Template
=======================

## version control

be sure to have the correct version setup:

|Chisel|Scala|Java|
|:--:|:--:|:--:|
|3.5.6|2.13.10|17|
|3.6.1|2.13.14|22|
|5.3.x|2.13.14|22|
|6.5.x|2.13.14|22|

## Suggested workflow

1. design your chisel module under ./src/main/scala/PACKAGE/MODULE.scala
2. write testbench under ./src/test/scala/PACKAGE/TESTBENCH.scala
3. modified the config panel in Makefile
4. run `make clean` to clean existing build, then run `make test` and `make run` to debug
5. move finished design (.sv file) to ./finals folder
5. add finished module and testbench and systemverilog to codecompanion-workspace.json

## Designing with Chisel

### Types and constants

#### basic Chisel types:

- Bool(): one bit signal, no need for a Width arg
- **Bits(n.W)**: vector of n bits in (W)idth with no meaning
- **UInt(n.W)**: vector of n bits in (W)idth which is interpreted as (U)nsigned Int
- **SInt(n.W)**: vector of n bits in (W)idth which is interpreted as (S)igned Int(in 2's complement)

assign a value to these types to form an **constant**

```scala

255.U(8.W) // decimal value
"hff".U(8.W) // hex value
"o377".U(8.W) // octal value
"b1111_1111".U(8.W) // binaru value, _ is ignored and just to make code more readable

// Bool can be assigned in a different way
true.B
false.B
```

> [!TIP]
> chars represents its ASCII value and can be directly used, which is convenient
> 
> ```scala
> val aChar = 'A'.U(8.W)
> ```

#### abstracted contructs

```scala
// 1. Bundle: a class of signals with different types, compares to struct in C
// wire type Bundle
class Channel() extends Bundle {
    val data = UInt(32.W)
    val valid = Bool()
}
val ch = Wire(new Channel())
ch.data := 123.U
ch.valid := true.B
// reg type Bundle
class Channel() extends Bundle {
    val data = UInt(32.W)
    val valid = Bool()
}
val initVal = Wire(new Channel())
initVal.data := 0.U
initVal.valid := false.B
val channelReg = RegInit(initVal)

// 2. Vec: an indexed collection of signals of the same type, compare to array in C
// a Vec is mainly used to:
//      1). do dynamic addressing in hardwire, aka multiplexer
//      2). contruct a register file
//      3). parametrization of number of ports
// Overall, it is used to map lists of signals to an index table

// wire type Vec
val v = Wire(Vec(3, UInt(4.W))) // equal to a MUX
v(0) := 1.U
v(1) := 2.U
v(2) := 3.U
val index = 1.U(2.W)
val result = v(index)
// reg type Vec
val vReg = Reg(Vec(3, UInt(8.W))) // equal to a regfile
val dout = vReg(rdIdx) // read
vReg(wrIdx) := din // write

// 3. combining the two types
// a Vec of Bundle
val vecBundle = Wire(Vec(8, new Channel())) // don't forget the 'new'
// a Bundle with Vec
class BundleVec extends Bundle {
    val field = UInt(8.W)
    val vector = Vec(4, UInt(8.W))
}
```

> [!caution]
> the types above are just Chisel types, they don't represent hardware
> To generate actual hardwire, wrap them in the hardware types below!

#### basic hardware type

- Wire: combinational logic
- Reg: register
- IO: ports

```scala
// use = to generate hardwire
val number = Wire(UInt())
val reg = Reg(SInt())
// use := to connect existing hardwires/constants
number := 10.U
reg := -3.S

// it's always safer to generate hardware with default values(to prevent latches)
val number = WireDefault(0.U(4.W))
val reg = RegInit(0.S(8.W))
```

### Basic grammar

```scala
// 1. logic operations
val and = a & b // bitwise and
val or = a | b // bitwise or
val xor = a ^ b // bitwise xor
val not = ~a    // bitwise negation
val lnot = !a   // a must be Bool, see also &&, ||

// 2. arithmetic operations
val add = a + b // result.W = max(a.W, b.W)
val sub = a - b // result.W = max(a.W, b.W)
val neg = -a    // result.W = a.W
val mul = a * b // result.W = a.W + b.W
val div = a / b // result.W = a.W
val mod = a % b // result.W = a.W
val equal = a === b // returns bool
val neq = a =/= b // return bool
val compare = a > b // return bool, see also >=, <, <=
val shift = a << b  // sign extend on SInt

// 3. assign value after definition
val w = Wire(UInt())
w := a & b

// 4. extraction and concatenation
val x = -1.U(32.W)
val extractOneBit = x(31)
val extractField = x(7,0) // index starts at 0

val lowByte = 'O'.U(8.W)
val highByte = 'N'.U(8.W)
val word = highByte ## lowByte // concatenate
val longerWord = Cat(highByte, lowByte, ...) // concatenate

// 5. advanced extraction and concatenation
val reduction = x.andR  // and reduction, returns a Bool. see also .orR, xorR
val replication = Fill(n,x) // replicate x n times
```

> [!TIP]
> In Chisel3, partial assignments are not allowed, for example, this leads to error:
> ```scala
> val assignWord = Wire(UInt(16.W))
> assignWord(7, 0) := lowByte
> assignWord(15, 8) := highByte
>```
> possible workarounds are using Bundle or Vec:
> ```scala
> val assignWord = Wire(UInt(16.W))
> class Split extends Bundle {
>   val high = UInt(8.W)
>   val low = UInt(8.W)
> }
> val split = Wire(new Split())
> split.low := lowByte
> split.high := highByte
> assignWord := split.asUInt // asUInt method converts a bundle to UInt, but the order is not defined
>```
> or
> ```scala
> val assignWord = Wire(UInt(16.W))
> val vecResult = Wire(Vec(2, UInt(8.W)))
> vecResult(0) := lowByte
> vecResult(1) := highByte
> assignWord := vecResult.asUInt // use asUInt method in Vec to avoid order problem
>```

### Builtin Modules

#### MUX
```scala

val result = Mux(sel, a, b) // sel is a Bool and a, b must be the same type. return a when true.B

val v = Wire(Vec(3, UInt(4.W))) // for larger MUX, use a Vec
v(0) := a
v(1) := b
v(2) := c
val index = 1.U(2.W)
val result = v(index)
// or more concisely
val defVecSig = VecInit(a, b, c)
val vecOutSig = defVecSig(sel)
```

#### REG

```scala

// REGISTER: contains synchronous reset and clk that triggers at posedge
val reg = RegInit(0.U(8.W)) // a 8 bit reg that resets to 0.U
reg := data // data to update at each clk cycle
val output = reg // wire driven by reg

// or use RegNext method
val nextReg = RegNext(data) // use RegNext mothod to create a reg with no reset and no sepcified width that has data as input
val nextReg = RegNext(data, 0.U) //however, you can pass a initial value (to tradeoff the loss of having no reset val)

// for multiple regs like a reg_file, use Vec
val vReg = Reg(Vec(3, UInt(8.W)))
val dout = vReg(rdIdx) // read
vReg(wrIdx) := din // write
//  or create a reg_file with init value
val initReg = RegInit(VecInit(0.U(3.W), 1.U, 2.U))
val resetVal = initReg(sel)
initReg(0) := a
initReg(1) := b
initReg(2) := c
//  if the reg_file is too large to type all the init values, use Seq.fill
val resetRegFile = RegInit(Vecinit(Seq.fill(32)(0.U(32.W))))
val rdRegFile = resetRegFile(sel)
```

#### COUNTER

```scala

// COUNTER: could be realized with a reg
val cntReg = RegInit(0.U(8.W))
cntReg := Mux(cntReg === 9.U, 0.U, cntReg + 1.U) // count from 0 to 9
```


## testing your design

In chisel, you can run unit test or regression test with **chiseltest**,
or generate **waveform** for more complex designs

### chiseltest

1. write a test class in a .scala file under /src/test/scala
2. run `sbt "testOnly packageName.testCleassName"` to run that test
3. run `sbt test` to run all tests at once

### Waveforms

chisel testers can generate a .vcd file that includes all regs and IO signals.
to do so, you can:

1. modified the Makefile, which applies an api
2. pass a suffix: `sbt "testOnly <testname> -- -DwriteVcd=1"`
3. or write in test code:
```scala

"foo" should "bar" in {
    test(new dut).withAnnotations(Seq(WriteVcdAnnotation)) {...}
}
```
