################### Config panel ####################
TOPMODULE ?= example.moduleExample  # module to build
TEST ?= example.testExample         # test to run

# SystemVerilog is generated at ./generated, change this in App class instance in your module

# sbt test options
GEN_VCD ?= false                    # generate .vcd file
VCD_DIR = generated/vcds           # the path of .vcd file
#####################################################

# Derived arguments
TEST_OPTS := $(if $(filter true, $(GEN_VCD)), -- -DwriteVcd=1)

all: run

.PHONY:run
run:
	@echo "========== generating SystemVerilog for $(TOPMODULE) =========="
	sbt "runMain $(TOPMODULE)"

.PHONY:test
test:
	@echo "========== runing test $(TEST) =========="
	sbt "testOnly $(TEST) $(TEST_OPTS)"
	@$(MAKE) move_vcd

.PHONY:move_vcd
move_vcd:
	-@mkdir -p $(VCD_DIR)
	-@find ./test_run_dir -name "*.vcd" -exec mv -v {} $(VCD_DIR) \; || true
	@echo "========== VCD files moved to: $(VCD_DIR) =========="


.PHONY:clean
clean:
	@echo "========== cleaning project =========="
	sbt clean
	-rm -rf ./generated
	-rm -rf ./test_run_dir
	-rm -rf ./target

.PHONY:help
help:
	@echo "Targets:"
	@echo "	run			Generate SystemVerilog(requires TOPMODULE)"
	@echo " test		Run tests(requires TEST)"
	@echo " clean		Remove all build artifacts"
	@echo "	all			Default target(run)"
