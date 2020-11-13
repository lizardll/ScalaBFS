package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._

/*  push -> pull mode
*   p1 read current_frontier -> p1 read visited_map                         √
*   p2 read visited_map -> p2 read current_frontier                         √
*   p2 write next_frontier -> crossbar write visited_map + next_frontier    √
*   visited_map will be read and write at the same time (write first)
*/


class Frontier_IO(implicit val conf : HBMGraphConfiguration) extends Bundle{
    //Input
    val frontier_count = Flipped(Decoupled(UInt(conf.Data_width.W)))        // input count of the required current_frontier
    val write_frontier = Vec(2, Flipped(Decoupled(UInt(conf.Data_width.W))))// input 2 next_frontier you want to write
    val frontier_flag = Input(UInt(1.W))    // input flag mark which frontier to use as current_frontier or next_frontier
    val p2_end = Input(Bool())           // input p2 finish signal
    val bram_clock = Input(Clock())            // input clock with 2x frenquency. used by bram.
    val start = Input(Bool())       // input start  
    val node_num = Input(UInt(conf.Data_width.W))
    val push_or_pull_state = Input(UInt(1.W))   //input flag mark push or pull state
    val level = Input(UInt(conf.Data_width.W))
    val uram_addr_a = Input(UInt(conf.Addr_width_uram.W))
    val uram_addr_b = Input(UInt(conf.Addr_width_uram.W))
    
    //Output
    val frontier_value = Decoupled(UInt(conf.Data_width_bram.W))  // output frontier data
    val end = Output(Bool())    // output end signal
    val last_iteration = Output(Bool())     // output. write next frontier in last iteration or not.
    val bram_from_p2 = new bram_controller_IO
    val uram_out_a = Output(UInt(conf.Data_width_uram.W))
    val uram_out_b = Output(UInt(conf.Data_width_uram.W))
    val frontier_pull_count = Output(UInt(conf.Data_width.W)) // pull crossbar count

}

class Frontier (val num :Int) (implicit val conf : HBMGraphConfiguration) extends Module{
    val io = IO(new Frontier_IO())
    dontTouch(io)
    io.frontier_value.valid := false.B
    io.frontier_value.bits := DontCare
    io.end := true.B

    val last_iteration_reg = RegInit(false.B)
    io.last_iteration := last_iteration_reg

    // two frontiers
    val frontier_0 = withClock(io.bram_clock){Module(new bram_controller(num, 1))}
    val frontier_1 = withClock(io.bram_clock){Module(new bram_controller(num, 2))}
    val uram = Module(new uram_controller(num, 3))
    frontier_0.io := DontCare
    frontier_0.io.ena := true.B
    frontier_0.io.enb := true.B
    frontier_0.io.clka := io.bram_clock
    frontier_0.io.clkb := io.bram_clock
    frontier_0.io.wea := false.B
    frontier_0.io.web := false.B
    frontier_0.io.wmode := 0.U
    frontier_1.io := DontCare
    frontier_1.io.ena := true.B
    frontier_1.io.enb := true.B
    frontier_1.io.clka := io.bram_clock
    frontier_1.io.clkb := io.bram_clock
    frontier_1.io.wea := false.B
    frontier_1.io.web := false.B
    frontier_1.io.wmode := 0.U

    uram.io := DontCare
    uram.io.clka := clock //io.bram_clock
    uram.io.clkb := clock //io.bram_clock
    uram.io.wea := 0.U
    uram.io.web := 0.U
    io.uram_out_a := uram.io.douta
    io.uram_out_b := uram.io.doutb
    io.uram_addr_a <> uram.io.addra
    io.uram_addr_b <> uram.io.addrb
    
    // input Queue
    val q_frontier_count = Queue(io.frontier_count, conf.q_p1_to_frontier_len)
    val q_write_frontier_0 = Queue(io.write_frontier(0), conf.q_p2_to_frontier_len)
    val q_write_frontier_1 = Queue(io.write_frontier(1), conf.q_p2_to_frontier_len)
    q_frontier_count.ready := false.B
    q_write_frontier_0.ready := false.B
    q_write_frontier_1.ready := false.B

    // counter
    val (count_wf0, _) = Counter(q_write_frontier_0.ready && q_write_frontier_0.valid, 2147483647)
    val (count_wf1, _) = Counter(q_write_frontier_1.ready && q_write_frontier_1.valid, 2147483647)
    io.frontier_pull_count := count_wf0 + count_wf1

    //as designed , at beginning io.p2_end is high. should wait it low and high again.
    val p2_end_flag = RegInit(0.U(1.W))
    when(io.p2_end === false.B){p2_end_flag := 1.U}

    val clear_addr = RegInit(0.U(conf.Addr_width.W))
    val state0 :: state1 :: state2 :: state_write_uram :: Nil = Enum(4)
    val stateReg = RegInit(state2)

    io.bram_from_p2 := DontCare
    //for visited_map
    val visited_map = withClock(io.bram_clock){Module(new bram_controller(num, 0))}
    visited_map.io := DontCare
    // push mode, read and write
    visited_map.io.clka := io.bram_clock
    visited_map.io.clkb := io.bram_clock
    visited_map.io.ena := true.B
    visited_map.io.enb := true.B
    when(io.push_or_pull_state === 0.U){
        visited_map.io.addra := io.bram_from_p2.addra
        visited_map.io.addrb := io.bram_from_p2.addrb

        // visited_map.io.clka := io.bram_from_p2.clka
        // visited_map.io.clkb := io.bram_from_p2.clkb
        visited_map.io.wea := io.bram_from_p2.wea
        visited_map.io.web := io.bram_from_p2.web
        visited_map.io.wmode := io.bram_from_p2.wmode
        visited_map.io.nodea := io.bram_from_p2.nodea
        visited_map.io.nodeb := io.bram_from_p2.nodeb
        io.bram_from_p2.douta := visited_map.io.douta
        io.bram_from_p2.doutb := visited_map.io.doutb
    }
    // pull mode, current_frontier is frontier_0, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 0.U){
        frontier_0.io.addra := io.bram_from_p2.addra
        frontier_0.io.addrb := io.bram_from_p2.addrb
        io.bram_from_p2.douta := frontier_0.io.douta
        io.bram_from_p2.doutb := frontier_0.io.doutb
    }
    // pull mode, current_frontier is frontier_1, read only
    when(io.push_or_pull_state === 1.U && io.frontier_flag === 1.U){
        frontier_1.io.addra := io.bram_from_p2.addra
        frontier_1.io.addrb := io.bram_from_p2.addrb
        io.bram_from_p2.douta := frontier_1.io.douta
        io.bram_from_p2.doutb := frontier_1.io.doutb
    }

    // for pull mode
    val port_a_is_writing_flag = Wire(Bool())  // visited_map is write first in pull mode
    val port_b_is_writing_flag = Wire(Bool())  // visited_map is write first in pull mode
    port_a_is_writing_flag := false.B
    port_b_is_writing_flag := false.B

    // for write level
    // val node_a0 = RegInit(0.U(conf.Data_width.W))
    // val node_a1 = RegInit(0.U(conf.Data_width.W))
    // val frontier_a = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_a0 = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_a1 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_a0 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_a1 = RegInit(0.U(conf.Data_width.W))
    // val node_b0 = RegInit(0.U(conf.Data_width.W))
    // val node_b1 = RegInit(0.U(conf.Data_width.W))
    // val frontier_b = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_b0 = RegInit(0.U(conf.Data_width.W))
    // val count_node_in_frontier_b1 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_b0 = RegInit(0.U(conf.Data_width.W))
    // val node_num_in_frontier_b1 = RegInit(0.U(conf.Data_width.W))

    switch(stateReg){
        is(state0){
            io.end := false.B
            //state 0 read and write
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := true.B
            q_write_frontier_1.ready := true.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 0.U
            frontier_1.io.wmode := 0.U
            
            when(q_write_frontier_0.valid){
                last_iteration_reg := false.B
                //write port a in next frontier
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.wea := true.B
                    // convert the total number of points to the number inside the pipeline
                    visited_map.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    visited_map.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                    port_a_is_writing_flag := true.B   // port a is write first
                }
                when(io.frontier_flag === 0.U){
                    //now next frontier is frontier_1
                    frontier_1.io.wea := true.B
                    frontier_1.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_1.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                .otherwise{
                    //now next frontier is frontier_0
                    frontier_0.io.wea := true.B
                    frontier_0.io.nodea := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_0.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                uram.io.wea := 1.U << ((Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U)
                uram.io.dina := io.level << (8.U * ((Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U))
                uram.io.addra := (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Write_width_uram.U
            }
            when(q_write_frontier_1.valid){
                last_iteration_reg := false.B
                //write port b in next frontier
                when(io.push_or_pull_state === 1.U){
                    visited_map.io.web := true.B
                    visited_map.io.nodeb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    visited_map.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                    port_b_is_writing_flag := true.B
                }
                when(io.frontier_flag === 0.U){
                    //now next frontier is frontier_1
                    frontier_1.io.web := true.B
                    frontier_1.io.nodeb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_1.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                .otherwise{
                    //now next frontier is frontier_0
                    frontier_0.io.web := true.B
                    frontier_0.io.nodeb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Data_width_bram.U
                    frontier_0.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U
                }
                uram.io.web := 1.U << ((Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U)
                uram.io.dinb := io.level << (8.U * ((Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) % conf.Write_width_uram.U))
                uram.io.addrb := (Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Write_width_uram.U
            }
            // visited_map is write first in pull mode
            // when(q_frontier_count.valid && io.frontier_value.ready && (!port_a_is_writing_flag || !port_b_is_writing_flag)){
            when(q_frontier_count.valid && io.frontier_value.ready && (!q_write_frontier_0.valid || !q_write_frontier_1.valid || io.push_or_pull_state === 0.U )){
                // q_frontier_count.ready := true.B
                // read visited_map in pull mode
                when(io.push_or_pull_state === 1.U){
                    // when(!q_write_frontier_0.valid && !q_write_frontier_0.valid){         // port a & b are free
                    when(!q_write_frontier_0.valid){ 
                        q_frontier_count.ready := true.B
                        visited_map.io.addra := q_frontier_count.bits
                        io.frontier_value.valid := true.B
                        io.frontier_value.bits := visited_map.io.douta    
                    }
                    // .elsewhen(!q_write_frontier_0.valid && (q_frontier_count.bits =/= ((Custom_function3.low(q_write_frontier_1.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U))){         // port a is free
                    //     q_frontier_count.ready := true.B
                    //     visited_map.io.addra := q_frontier_count.bits
                    //     io.frontier_value.valid := true.B
                    //     io.frontier_value.bits := visited_map.io.douta    
                    // }
                    // .elsewhen(!q_write_frontier_1.valid && (q_frontier_count.bits =/= ((Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U))){         // port b is free
                    //     q_frontier_count.ready := true.B
                    //     visited_map.io.addrb := q_frontier_count.bits
                    //     io.frontier_value.valid := true.B
                    //     io.frontier_value.bits := visited_map.io.doutb    
                    // }
                }
                
                // .elsewhen(io.push_or_pull_state === 1.U && !port_b_is_writing_flag &&((q_frontier_count.bits =/= (Custom_function3.low(q_write_frontier_0.bits) / conf.numSubGraphs.U) / conf.Data_width_bram.U))){    // port b is free 
                //     q_frontier_count.ready := true.B
                //     visited_map.io.addrb := q_frontier_count.bits
                //     io.frontier_value.valid := true.B
                //     io.frontier_value.bits := visited_map.io.doutb    
                // }

                // read from current frontier in push mode
                when(io.push_or_pull_state === 0.U ){
                    when(io.frontier_flag === 0.U){
                        q_frontier_count.ready := true.B
                        frontier_0.io.addra := q_frontier_count.bits
                        io.frontier_value.valid := true.B
                        io.frontier_value.bits := frontier_0.io.douta
                    }
                    .otherwise{
                        q_frontier_count.ready := true.B
                        frontier_1.io.addra := q_frontier_count.bits
                        io.frontier_value.valid := true.B
                        io.frontier_value.bits := frontier_1.io.douta
                    }
                }

            }
            when(io.p2_end && p2_end_flag === 1.U && !q_frontier_count.valid && !q_write_frontier_0.valid && !q_write_frontier_1.valid){
                stateReg := state1
                clear_addr := 0.U
            }
        }
        is(state1){  // clear bits
            io.end := false.B
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := false.B
            q_write_frontier_1.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 1.U
            frontier_1.io.wmode := 1.U
            clear_addr := clear_addr + 4.U
            when(io.frontier_flag === 0.U){
                // now current frontier is frontier 0 , at next level it will become next frontier , so clear it
                frontier_0.io.wea := true.B
                frontier_0.io.web := true.B
                frontier_0.io.dina := 0.U
                frontier_0.io.dinb := 0.U
                frontier_0.io.addra := clear_addr
                frontier_0.io.addrb := clear_addr + 2.U

                // frontier_1.io.wea := false.B
                // frontier_1.io.web := false.B
                // frontier_1.io.addra := clear_addr
                // frontier_1.io.addrb := clear_addr + 1.U
                // node_num_in_frontier_a0 :=(PopCount(frontier_1.io.douta) + 1.U) / 2.U
                // node_num_in_frontier_a1 :=PopCount(frontier_1.io.douta) / 2.U
                // node_num_in_frontier_b0 :=(PopCount(frontier_1.io.doutb) + 1.U) / 2.U
                // node_num_in_frontier_b1 :=PopCount(frontier_1.io.doutb) /2.U
                // when(frontier_1.io.douta =/= 0.U){
                //     node_a0 := Custom_function.find_node(frontier_1.io.douta, conf.Data_width_bram, clear_addr)
                //     node_a1 := Custom_function.find_node(Custom_function.remove_one(frontier_1.io.douta), conf.Data_width_bram, clear_addr)
                //     // frontier_a0 := Custom_function.remove_one(Custom_function.remove_one(frontier_1.io.douta))
                //     frontier_a := Custom_function.remove_one(Custom_function.remove_one(frontier_1.io.douta))
                //     count_node_in_frontier_a0 := 0.U
                //     count_node_in_frontier_a1 := 0.U
                //     stateReg := state_write_uram
                // }
                // when(frontier_1.io.doutb =/= 0.U){
                //     node_b0 := Custom_function.find_node(frontier_1.io.doutb, conf.Data_width_bram, clear_addr + 1.U)
                //     node_b1 := Custom_function.find_node(Custom_function.remove_one(frontier_1.io.doutb), conf.Data_width_bram, clear_addr)
                //     // frontier_b0 := Custom_function.remove_one(Custom_function.remove_one(frontier_1.io.doutb))
                //     frontier_b := Custom_function.remove_one(Custom_function.remove_one(frontier_1.io.doutb))
                //     count_node_in_frontier_b0 := 0.U
                //     count_node_in_frontier_b1 := 0.U
                //     stateReg := state_write_uram
                // }
                when(clear_addr >= (io.node_num / conf.Data_width_bram.U)){ // >= -> >
                    stateReg := state2
                    io.end := true.B
                }

            }
            .otherwise{
                // now current frontier is frontier 1 , at next level it will become next frontier , so clear it
                frontier_1.io.wea := true.B
                frontier_1.io.web := true.B
                frontier_1.io.dina := 0.U
                frontier_1.io.dinb := 0.U
                frontier_1.io.addra := clear_addr
                frontier_1.io.addrb := clear_addr + 2.U

                // frontier_0.io.wea := false.B
                // frontier_0.io.web := false.B
                // frontier_0.io.addra := clear_addr
                // frontier_0.io.addrb := clear_addr + 1.U
                // node_num_in_frontier_a0 :=(PopCount(frontier_0.io.douta) + 1.U) / 2.U
                // node_num_in_frontier_a1 :=PopCount(frontier_0.io.douta) / 2.U
                // node_num_in_frontier_b0 :=(PopCount(frontier_0.io.doutb) + 1.U) / 2.U
                // node_num_in_frontier_b1 :=PopCount(frontier_0.io.doutb) /2.U
                // when(frontier_0.io.douta =/= 0.U){
                //     node_a0 := Custom_function.find_node(frontier_0.io.douta, conf.Data_width_bram, clear_addr)
                //     node_a1 := Custom_function.find_node(Custom_function.remove_one(frontier_0.io.douta), conf.Data_width_bram, clear_addr)
                //     // frontier_a0 := Custom_function.remove_one(Custom_function.remove_one(frontier_0.io.douta))
                //     frontier_a := Custom_function.remove_one(Custom_function.remove_one(frontier_0.io.douta))
                //     count_node_in_frontier_a0 := 0.U
                //     count_node_in_frontier_a1 := 0.U
                //     stateReg := state_write_uram
                // }
                // when(frontier_0.io.doutb =/= 0.U){
                //     node_b0 := Custom_function.find_node(frontier_0.io.doutb, conf.Data_width_bram, clear_addr + 1.U)
                //     node_b1 := Custom_function.find_node(Custom_function.remove_one(frontier_0.io.doutb), conf.Data_width_bram, clear_addr)
                //     // frontier_b0 := Custom_function.remove_one(Custom_function.remove_one(frontier_0.io.doutb))
                //     frontier_b := Custom_function.remove_one(Custom_function.remove_one(frontier_0.io.doutb))
                //     count_node_in_frontier_b0 := 0.U
                //     count_node_in_frontier_b1 := 0.U
                //     stateReg := state_write_uram
                // }
                when(clear_addr >= (io.node_num / conf.Data_width_bram.U)){
                    stateReg := state2
                    io.end := true.B
                }
            }
        }
        // is(state_write_uram){
        //     // need not to consider when nodea/nodeb > nodnum
        //     uram.io.wea0 := false.B
        //     uram.io.wea1 := false.B
        //     uram.io.web0 := false.B
        //     uram.io.web1 := false.B
        //     when((node_num_in_frontier_a0 === count_node_in_frontier_a0) &&
        //          (node_num_in_frontier_a1 === count_node_in_frontier_a1) &&
        //          (node_num_in_frontier_b0 === count_node_in_frontier_b0) &&
        //          (node_num_in_frontier_b1 === count_node_in_frontier_b1)){
        //         stateReg := state1
        //     }
        //     .otherwise{
        //         frontier_a := Custom_function.remove_one(Custom_function.remove_one(frontier_a))
        //         frontier_b := Custom_function.remove_one(Custom_function.remove_one(frontier_b))
        //         when(node_num_in_frontier_a0 < count_node_in_frontier_a0){
        //             uram.io.wea0 := true.B
        //             uram.io.addra0 := node_a0 
        //             uram.io.dina0 := io.level 
        //             node_a0 := Custom_function.find_node(frontier_a, conf.Data_width_bram, clear_addr - 2.U)
        //             node_num_in_frontier_a0 := node_num_in_frontier_a0 + 1.U
        //         }
        //         when(node_num_in_frontier_a1 < count_node_in_frontier_a1){
        //             uram.io.wea1 := true.B
        //             uram.io.addra1 := node_a1 
        //             uram.io.dina1 := io.level 
        //             node_a1 := Custom_function.find_node(Custom_function.remove_one(frontier_a), conf.Data_width_bram, clear_addr - 2.U)
        //             node_num_in_frontier_a1 := node_num_in_frontier_a1 + 1.U
        //         }
        //         when(node_num_in_frontier_b0 < count_node_in_frontier_b0){
        //             uram.io.web0 := true.B
        //             uram.io.addrb0 := node_b0 
        //             uram.io.dinb0 := io.level 
        //             node_b0 := Custom_function.find_node(frontier_b, conf.Data_width_bram, clear_addr - 1.U)
        //             node_num_in_frontier_b0 := node_num_in_frontier_b0 + 1.U
        //         }
        //         when(node_num_in_frontier_b1 < count_node_in_frontier_b1){
        //             uram.io.web1 := true.B
        //             uram.io.addrb1 := node_b1 
        //             uram.io.dinb1 := io.level 
        //             node_b1 := Custom_function.find_node(Custom_function.remove_one(frontier_b), conf.Data_width_bram, clear_addr - 1.U)
        //             node_num_in_frontier_b1 := node_num_in_frontier_b1 + 1.U
        //         }                   
        //         stateReg := state_write_uram
        //     }
        // }
        is(state2){
            io.end := true.B
            q_frontier_count.ready := false.B
            q_write_frontier_0.ready := false.B
            q_write_frontier_1.ready := false.B
            frontier_0.io.wea := false.B
            frontier_0.io.web := false.B
            frontier_1.io.wea := false.B
            frontier_1.io.web := false.B
            frontier_0.io.wmode := 0.U
            frontier_1.io.wmode := 0.U
            when(io.start){
                io.end := false.B
                stateReg := state0
                last_iteration_reg := true.B
            }
        }
    }
}

object Custom_function3{
    implicit val conf = HBMGraphConfiguration()
    def low(n : UInt) : UInt = 
        n(conf.crossbar_data_width - 1, 0)

}
