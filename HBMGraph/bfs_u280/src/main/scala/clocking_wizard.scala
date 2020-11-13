package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class clk_wiz_0 extends BlackBox{  
  val io = IO(new Bundle{
    // Clock out ports
    val clk_bram = Output(Clock())
    // Clock in ports
    val clock = Input(Clock())
  })

}


class rst_n_to_rst extends BlackBox{  
  val io = IO(new Bundle{
    // Clock out ports
    val reset = Output(Bool())
    val reset_n = Input(Bool())
  })

}
