package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

class bram_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_bram.W))
    val wea = Input(Bool())
    val douta = Output(UInt(conf.Data_width_bram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))
}

class uram_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width_uram.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_uram.W))
    val wea = Input(UInt((conf.Data_width_uram / 8).W))
    val douta = Output(UInt(conf.Data_width_uram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_uram.W))
    val web = Input(UInt((conf.Data_width_uram / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram.W))
}


// num is the channel num
// when bram_num = 0 means visited_map
// when bram_num = 1 or 2 means frontier

class bram(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new bram_IO)
	override def desiredName =
    if(bram_num < 3){
        "bram_" + num + "_" + bram_num
    }else{
        "uram_" + num
    }
}
class uram(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends BlackBox{
	val io = IO(new uram_IO)
	override def desiredName =
    if(bram_num < 3){
        "bram_" + num + "_" + bram_num
    }else{
        "uram_" + num
    }
}

class bram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    // bram io
    // val bram = new bram_IO
    val ena = Input(Bool())
    val addra = Input(UInt(conf.Addr_width.W))
    val clka = Input(Clock())
    val dina = Input(UInt(conf.Data_width_bram.W))
    val wea = Input(Bool())
    val douta = Output(UInt(conf.Data_width_bram.W))
    val enb = Input(Bool())
    val addrb = Input(UInt(conf.Addr_width.W))
    val clkb = Input(Clock())
    val dinb = Input(UInt(conf.Data_width_bram.W))
    val web = Input(Bool())
    val doutb = Output(UInt(conf.Data_width_bram.W))

    // wmode 0 : read-or-write 1 : write 2 data for clear
    val wmode = Input(UInt(1.W))
    // nodea and nodeb's value is between 0 and conf.Data_width_bram - 1
    val nodea = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))
    val nodeb = Input(UInt((conf.Data_width_bram.U.getWidth - 1).W))
    // only visited_map need these two signals to show if the node is visited 
    // val visited_a = Output(UInt(1.W))
    // val visited_b = Output(UInt(1.W))
}

class bram_controller(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new bram_controller_IO)
    dontTouch(io)
    val ram = Module(new bram(num, bram_num))
    // init signals
    ram.io.ena := io.ena
	ram.io.enb := io.enb
	ram.io.addra := io.addra
	ram.io.addrb := io.addrb
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := false.B
	ram.io.web := false.B
    ram.io.dina := DontCare
	ram.io.dinb := DontCare
	io.douta := ram.io.douta
    io.doutb := ram.io.doutb

    // val visited_a = RegInit(0.U(1.W))
    // val visited_b = RegInit(0.U(1.W))
    // io.visited_a := visited_a
    // io.visited_b := visited_b
    val cnt = RegInit(0.U(1.W))
    // init cnt
    cnt := 0.U
    when((io.wea || io.web) && io.wmode === 0.U){  //need to write data
        when(cnt === 0.U){
            // read
            cnt := cnt + 1.U
            ram.io.wea := false.B
            ram.io.web := false.B
            // ram.io.addra := io.addra
            // ram.io.addrb := io.addrb
        }
        .otherwise{ //write
            cnt := cnt + 1.U
            // visited_map result
            // visited_a := ram.io.douta(io.nodea)
            // visited_b := ram.io.doutb(io.nodeb)

            when(io.addra === io.addrb && io.wea && io.web){ // just write in port a
                // // we only write when the node is not visited or the node is not in frontier
                // when(ram.io.douta(io.nodea) === 0.U || ram.io.doutb(io.nodeb) === 0.U){
                ram.io.wea := true.B
                ram.io.web := false.B
                ram.io.dina := ram.io.douta | (1.U << io.nodea) | (1.U << io.nodeb)
                // }
            }
            .otherwise{
                // write in port a
                // when(ram.io.douta(io.nodea) === 0.U){
                ram.io.wea := true.B & io.wea
                ram.io.dina := ram.io.douta | (1.U << io.nodea) 
                // }
                // write in port b
                // when(ram.io.doutb(io.nodeb) === 0.U){
                ram.io.web := true.B & io.web
                ram.io.dinb := ram.io.doutb | (1.U << io.nodeb)
                // }                
            }
        }
    }
    // for clear
    .elsewhen((io.wea || io.web) && io.wmode === 1.U){
        when(io.wea){
            when(cnt === 0.U){
                cnt := cnt + 1.U
                ram.io.wea := io.wea
                ram.io.addra := io.addra
                ram.io.dina := io.dina
            }
            .otherwise{
                cnt := cnt + 1.U
                ram.io.wea := io.wea
                ram.io.addra := io.addra + 1.U
                ram.io.dina := io.dina
            }
           
        }
        when(io.web){
            when(cnt === 0.U){
                cnt := cnt + 1.U
                ram.io.web := io.web
                ram.io.addrb := io.addrb
                ram.io.dinb := io.dinb
            }
            .otherwise{
                cnt := cnt + 1.U
                ram.io.web := io.web
                ram.io.addrb := io.addrb + 1.U
                ram.io.dinb := io.dinb
            }
        }
    }
    .otherwise{
        ram.io.wea := false.B
        ram.io.web := false.B
    }
}


class uram_controller_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{

    val clka = Input(Clock())
    val clkb = Input(Clock())    
    
    val addra = Input(UInt(conf.Addr_width_uram.W))
    // val addra1 = Input(UInt(conf.Addr_width_uram.W))
    val dina = Input(UInt(conf.Data_width_uram.W))
    // val dina1 = Input(UInt(conf.Data_width_uram.W))
    val wea = Input(UInt((conf.Data_width_uram / 8).W))
    val douta = Output(UInt(conf.Data_width_uram.W))
    // val wea1 = Input(UInt(conf.Data_width_uram / 8).W))
    val addrb = Input(UInt(conf.Addr_width_uram.W))
    // val addrb1 = Input(UInt(conf.Addr_width_uram.W))
    val dinb = Input(UInt(conf.Data_width_uram.W))
    // val dinb1 = Input(UInt(conf.Data_width_uram.W))
    val web = Input(UInt((conf.Data_width_uram / 8).W))
    // val web1 = Input(UInt(conf.Data_width_uram / 8).W))
    val doutb = Output(UInt(conf.Data_width_uram.W))

}

class uram_controller(val num : Int, bram_num : Int)(implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new uram_controller_IO)
    dontTouch(io)
    val ram = Module(new uram(num, bram_num))
    // init signals
    ram.io.ena := true.B
	ram.io.enb := true.B
	ram.io.addra := DontCare
	ram.io.addrb := DontCare
	ram.io.clka := io.clka
	ram.io.clkb := io.clkb
	ram.io.wea := false.B
	ram.io.web := false.B
    ram.io.dina := DontCare
	ram.io.dinb := DontCare
    io.douta := ram.io.douta
    io.doutb := ram.io.doutb


    // val cnt = RegInit(0.U(1.W))
    // cnt := cnt + 1.U

    // when(cnt === 0.U){
    ram.io.addra := io.addra
    ram.io.addrb := io.addrb
    ram.io.wea   := io.wea
    ram.io.web   := io.web
    ram.io.dina  := io.dina
    ram.io.dinb  := io.dinb
    // }.otherwise{
    //     ram.io.addra := io.addra1
    //     ram.io.addrb := io.addrb1
    // 	ram.io.wea   := io.wea1
	//     ram.io.web   := io.web1
    //     ram.io.dina  := io.dina1
	//     ram.io.dinb  := io.dinb1
    // }
}
