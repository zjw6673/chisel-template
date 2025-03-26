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

## suggested workflow

1. design your chisel module under ./src/main/scala/PACKAGE/MODULE.scala
2. write testbench under ./src/test/scala/PACKAGE/TESTBENCH.scala
3. modified the config panel in Makefile
4. run `make clean` to clean existing build, then run `make test` and `make run` to debug
5. move finished design (.sv file) to ./finals folder
5. add finished module and testbench and systemverilog to codecompanion-workspace.json

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
```
"foo" should "bar" in {
    test(new dut).withAnnotations(Seq(WriteVcdAnnotation)) {...}
}
```
