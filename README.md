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
- Mem/SyncReadMem: memory

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

#### bits operations

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

#### bulk connection: fast way to connect modules

use `<>` to connect IO bundles quickly between modules. When applied, it searches for Input, Output pairs
with the same name and connects them

for example:

```scala
class Fetch extends Module {
    val io = IO(new Bundle {
        val instr = Output (UInt (32.W))
        val pc = Output (UInt (32.W))
    })
    // ... Implementation of fetch
}

class Decode extends Module {
    val io = IO(new Bundle {
        val instr = Input(UInt (32.W))
        val pc = Input(UInt (32.W))
        val aluOp = Output(UInt (5.W))
        val regA = Output (UInt (32.W))
        val regB = Output (UInt (32.W))
    })
    // ... Implementation of decode
}

class Execute extends Module {
    val io = IO(new Bundle {
        val aluOp = Input(UInt (5.W))
        val regA = Input(UInt (32.W))
        val regB = Input(UInt (32.W))
        val result = Output(UInt (32.W))
    })
    // ... Implementation of execute
}

val fetch = Module(new Fetch())
val decode = Module(new Decode())
val execute = Module(new Execute)

// connect the ports between submodules like this
fetch.io <> decode.io
decode.io <> execute.io

// and ports can also be connected between current and submodule
io <> execute.io
```

### Basic Modules

#### MUX
```scala
// the smallest MUX is a 2to1 MUX
val result = Mux(sel, a, b) // sel is a Bool and a, b must be the same type. return a when true.B

// construct a chain of Mux2to1 with when, .elsewhen, .otherwise
val w = WireDefault(0.U(8.W))
when (cond1) {
    w := 1.U
} .elsewhen(cond2) {
    w := 2.U
} .otherwise { // remember to set a default value
    w := 3.U
}

// for larger MUX, use a Vec
val v = Wire(Vec(3, UInt(4.W)))
v(0) := a
v(1) := b
v(2) := c
val index = 1.U(2.W)
val result = v(index)
// or write more concisely
val defVecSig = VecInit(a, b, c)
val vecOutSig = defVecSig(sel)
```

#### DECODER

```scala
import chisel3.util._

// a 24 decoder
val result = WireDefault(0.U(4.W)) // be sure to provide a default value
switch(sel) {
    is (0.U) { result := "b0001".U }
    is (2.U) { result := "b0010".U }
    is (3.U) { result := "b0100".U }
    is (4.U) { result := "b1000".U }
}
// or more concisely
result := 1.U << sel
```

#### ENCODER

```scala
import chisel3.util._

// a 42 encoder
val result = WireDefault(0.U(2.W)) // be sure to provide a default value
switch(a) {
    is ("b0001".U) { result := 0.U }
    is ("b0010".U) { result := 1.U }
    is ("b0100".U) { result := 2.U }
    is ("b1000".U) { result := 3.U }
}
// or more concisely with a generator loop
val v = Wire(Vec(16, UInt(4.W))) // a 16to4 encoder
v(0) := 0.U
for (i <- 1 until 16) { // loops i from 1 to 15
    v(i) = Mux(hotIn(i), i.U, 0.U) | v(i-1)
}
val result = v(15)

// for priority encoders, use a arbiter and a normal encoder
```

#### ARBITER

an arbiter arbitrates requests from **several clients** to a **shared resource**

A priority arbiter choose clients with a priority, the lower the bit number, the higher the priority.
e.g. 0101 generates 0001
``` scala
// write in a table with switch for small arbiters
import chisel3.util._

val grant = WireDefault("b000".U(4.W))
val request = VecInit(a, b, c)

switch(request) {
    is ("b000".U) { grant := "b000".U }
    // ...
    is ("b111".U) { grant := "b001".U }
}

// or use the logic below
val request = VecInit(a, b, c)
val grant = VecInit(false.B, false.B, false.B)
val notGrantedYet = VecInit(false.B, false.B) // record if some previous wire has already been granted

grant(0) := request(0)
notGrantedYet(0) := !grant(0)

grant(1) := request(1) && notGrantedYet(0)
notGrantedYet(1) := notGrantedYet(0) && !grant(1)

grant(2) := request(2) && notGrantedYet(1)

// This logic can be easily applied to large arbitors
val grant = VecInit.fill(n)(false.B)
val notGrantedYet = VecInit.fill(n)(false.B)

grant(0) := request(0)
notGrantedYet(0) := !grant(0)
for (i <- 1 until n) {
    grant(i) := request(i) && notGrantedYet(i-1)
    notGrantedYet(i) := !grant(i) && notGrantedYet(i-1)
}
```

A fair arbiter choose clients by remembering the last arbitration

#### COMPARATOR

```scala

val equ = a === b
val gt = a > b
```
since they are too easy, thay are usually directly used instead of wrapped into a module

#### REG

```scala

// REGISTER: contains synchronous reset and clk that triggers at posedge
val reg = RegInit(0.U(8.W)) // a 8 bit reg that resets to 0.U
reg := data // data to update at each clk cycle
val output = reg // wire driven by reg

// or use RegNext method
val nextReg = RegNext(data) // use RegNext mothod to create a reg with no reset and no sepcified width that has data as input
val nextReg = RegNext(data, 0.U) //however, you can pass a initial value (to tradeoff the loss of having no reset val)

// to instanciate a REG with enable signal, use method RegEnable()
val resetEnableReg = RegEnable(inVal, 0.U(8.W), enable) // input data, reset value and enable signal respectively

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

> [!TIP]
> `RegNext()` method is useful in creating a one-clk delay, for example
> ```scala
> // this code detects the posedge
> val risingEdge = din & !RegNext(din)
> ```

#### COUNTER

```scala

// COUNTER: could be realized with a reg
val cntReg = RegInit(0.U(8.W))
cntReg := Mux(cntReg === 9.U, 0.U, cntReg + 1.U) // count from 0 to 9
val tick = cntReg === 9.U // a tick of lower frequency generated by this counter

// to count on events instead of posedge
val cntEventReg = RegInit(0.U(8.W))
when (event) {
    cntEventReg := cntEventReg + 1.U
}

// counters requires a comparetor, which could be optimized in a clever way:
val MAX = (N-2).S(8.W)
val cntReg = RegInit(MAX)
io.tick = false.B

cntReg := cntReg - 1.S
when (cntReg(7)) {  // utilize the sign bit of a signed number to mark MIN
    cntReg := MAX
    io.tick = true.B
}

// TIMER: a special type of counter that acts like an alarm clk
val cntReg = RegInit(0.U(8.W))
val done = cntReg === 0.U // when counts to 0, set alarm

val next = WireDefault(0.U(8.W))
when (load) {   // load number and startup counter
    next := din
} .elsewhen (!done) {
    next := cntReg - 1.U
}
cntReg := next
```

#### SHIFT REG

```scala

val shiftReg = RegInit(0.U(4.W))
shiftReg := Mux(load, parallelIn, din ## shiftReg(3, 1)) // one bit input or paralel load
dout := shiftReg(0) // one bit output
parallelOut := shiftReg // parallel output
```

#### MEM

define a mem hardware:

``` scala

class Memory extends Module {
    val io = IO(new Bundle {
        val rdAddr = Input(UInt(10.W))
        val rdData = Output(UInt(8.W))
        val wrAddr = Input(UInt(10.W))
        val wrData = Output(UInt(8.W))
        val wrEna = Input(Bool())
    })

    val mem = SyncReadMem(1024, UInt(8.W)) // use SyncReadMem to create a mem

    io.rdData := mem.read(io.rdAddr) // use read method to read
    when (wrEna) {
        mem.write(io.wrAddr, io.wrData) // use write method to write
    }
}
```

> [!CAUTION]
> writing and reading at the same addr(read-during-write) can cause undifined behaviors, thus should be avoided
> make sure to pass an extra arg to SyncReadMem to define this behavior:
> - SyncReadMem.WriteFirst: read the rdData
> - SyncReadMem.ReadFirst: read the old Data
> - SyncReadMem.Undefined: leave this behavior undefined

load files to mem:
```scala

val hello = "hello world"
val helloHex = hello.map(_.toInt.toHexString).mkString("\n") // generate file content
val file = new java.io.PrintWriter("hello.hex") // file name
file.write(helloHex) // write content to file with file name
file.close()

val mem = SyncReadMem(1024, UInt(8.W)) // 1 KB of mem
loadMemoryFromFileInline(mem, "hello.hex", firrtl.annotations.MemoryLoadFileType.Hex) // load by 4 bits(hex)
```

### Advanced: Input processing

Signals from external world could be **asynchronous**, **debouncing** and **spikey**
This should be tackled in the digital domain

the input processing should follow the these steps:

#### STEP1: Asynchronous input

asychronous inputs are inputs that are not synchronous to system clk, they may cause:

- **metastability**: where signals are between 0 and 1 before they stabilize
    - solution: **use two flip-flops at the input**, leaving more time for the signal to stabilize
    ```scala
    val btnSync = RegNext(RegNext(btn))
    ```
- **misregistrition**: if a asynchronous input changes close to posedge clk, due to delay, it may cause different usages of that input to be registered at different clk cycles

#### STEP2: Debouncing

Switches and buttons need time to transition between 0 and 1, during which they may **bounce** between 0 and 1
If not processed, multiple transition events may be detected, which is unwanted

solution: set a time filter by **sampling** the siganl with a time interval larger than the max_unstable_time

```scala
val fac = 100000000/100 // assume the clk is 100MHz, and we sample at 100Hz, so that's 100M/100 clk cycle per sample

val btnDebReg = Reg(Bool())
// create a counter to do that
val cntReg = RegInit(0.U(32.W))
val tick = cntReg === (fac-1).U // generate one tick per counter cycle
cntReg := cntReg + 1.U
when (tick) {
    cntReg := 0.U
    btnDebReg := btnSync    // update btnDebReg per tick
}
```

#### STEP3: Filtering spikes

Despite sampling, the input may still contain unwanted spikes in severe cases
This could be tackled with a **majority voting circuit**, that is, feed the output of samples into a shift register
to construct a parallel n-bit input history, and vote for the majority

NOTE: this step is seldom needed

```scala
val shiftReg = RegInit(0.U(3.W)) // this is a 3-bit majority voting circuit

when (tick) { // tirggered by tick
    // shift left and input
    shiftReg := shiftReg(1, 0) ## btnDebReg
}
// Majority voting
val btnClean = (shiftReg(2) & shiftReg(1)) | (shiftReg(2) & shiftReg(0)) | (shiftReg(1) & shiftReg(0))
```

## Naming Conventions

### Filenames

Filenames should consist of **case-sensitive** name of the **top-level** class it contains

### Packages

Packages should be named after the **pull path to get to the source** from scala/, with **all lowercase** and **no underscore**

### Imports

Avoid wildcard (._) imports except for chisel3._, and always put chisel3._ on top

```scala
// example
import chisel3._

import the.other.thing.that.i.reference.inline
import the.other.things.that.i.reference.{ClassOne, ClassTwo}
```

### Tests

- Test classes: named starting with the class they are testing, and end with Test
- Test files: should match the test class name, reside in /tests/
- Test package: should be composed of the package that contains the class under test

### Module classes and Code

use `lowerCamelCase` for variable and `UpperCamelCase` for classes
also use `UpperCamelCase` for constants, as they are actually objects in scala



for more info, checkout [this page](https://www.chisel-lang.org/docs/developers/style)

## Testing your design

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
