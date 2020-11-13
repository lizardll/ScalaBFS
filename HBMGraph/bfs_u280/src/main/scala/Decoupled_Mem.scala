package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._


class AXIAddress(val addrWidthBits: Int, val idBits: Int) extends Bundle {
    // address for the transaction, should be burst aligned if bursts are used
    val addr    = UInt(addrWidthBits.W)
    // // number of data beats -1 in burst: max 255 for incrementing, 15 for wrapping
    val len     = UInt(8.W)
    // transaction ID for multiple outstanding requests
    val id      = UInt(idBits.W)
    // size of data beat in bytes
    // set to UInt(log2Up((dataBits/8)-1)) for full-width bursts
    // val size    = UInt(log2Up((dataBits/8)-1))
    // // burst mode: 0 for fixed, 1 for incrementing, 2 for wrapping
    // val burst   = UInt(2.W)
    // // set to 1 for exclusive access
    // val lock    = Bool()
    // // cachability, set to 0010 or 0011
    // val cache   = UInt(4.W)
    // // generally ignored, set to to all zeroes
    // val prot    = UInt(3.W)
    // // not implemented, set to zeroes
    // val qos     = UInt(4.W)
}

class AXIWriteData(val dataWidthBits: Int) extends Bundle {
    val data    = UInt(dataWidthBits.W)
    val strb    = UInt((dataWidthBits/8).W)
    val last    = Bool()
}

class AXIWriteResponse(val idBits: Int) extends Bundle {
    val id      = UInt(idBits.W)
    val resp    = UInt(2.W)
}

class AXIReadData(val dataWidthBits: Int, val idBits: Int) extends Bundle {
    // 64 bits data can be divided into 2 32-bits data
    val data    = UInt((dataWidthBits).W)
    val id      = UInt(idBits.W)
    val last    = Bool()
    val resp    = UInt(2.W)
}

// Part II: Definitions for the actual AXI interfaces
class AXIMasterIF(val addrWidthBits: Int, val dataWidthBits: Int, val idBits: Int) extends Bundle {
    // write address channel
    val writeAddr   = Decoupled(new AXIAddress(addrWidthBits, idBits))
    // write data channel
    val writeData   = Decoupled(new AXIWriteData(dataWidthBits))
    // write response channel (for memory consistency)
    val writeResp   = Flipped(Decoupled(new AXIWriteResponse(idBits)))
  
    // read address channel
    val readAddr    = Decoupled(new AXIAddress(addrWidthBits, idBits))
    // read data channel
    val readData    = Flipped(Decoupled(new AXIReadData(dataWidthBits, idBits)))
}

// Read neighbour using burst and deal with the situation where neighbour size > 256
class Read_neighbour(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new Bundle {
        // input
        val readData = Flipped(Decoupled(new AXIReadData(64, conf.memIDBits))) //HBM data out
        val offsets = Input(new offsets)  // offsets
        val push_or_pull_state = Input(Bool()) // 0 for push

        //output
        val to_arbiter = Decoupled(new Bundle{
            val index = UInt(conf.Data_width.W)
            val burst_len = UInt(8.W) // in 64 bits
            val id = UInt(conf.memIDBits.W) // 0->index, 1->neighbour
        })

        // these are for counters
        val count_val_n0 = Output(UInt(conf.Data_width.W)) // burst number summation
        val queue_ready = Output(Bool())
        val queue_valid = Output(Bool())

        // these are for src_index_queue
        val src_q0_deq = Flipped(Decoupled(UInt(conf.Data_width.W)))
        val src_q1_enq = Decoupled(UInt(conf.Data_width.W))
    })
    val read_rsp :: loop :: Nil = Enum(2)
    val state = RegInit(read_rsp)
    val neighbour_count = RegInit(0.U(conf.Data_width.W))
    val temp_index = RegInit(0.U(conf.Data_width.W))
    val queue_readData = Module(new Queue(new AXIReadData(64, conf.memIDBits), conf.Mem_queue_readData_len)) // big queue to ensure no deadlock
    val burst_sum = RegInit(0.U(conf.Data_width.W))
    val before_4k_bound_count = Wire(UInt(conf.Data_width.W))
    before_4k_bound_count := DontCare
    val C_offset = Mux(io.push_or_pull_state, io.offsets.CSC_C_offset ,io.offsets.CSR_C_offset)


    burst_sum <> io.count_val_n0
    io.to_arbiter.bits <> DontCare

    // queue_readData <> io.readData
    queue_readData.io.enq.ready <> io.readData.ready
    queue_readData.io.enq.valid := io.readData.valid && io.readData.bits.id === 0.U
    queue_readData.io.enq.bits := io.readData.bits

    // queue_readData <> to_arbiter
    queue_readData.io.deq.ready := false.B
    io.to_arbiter.valid := false.B

    // for counter
    io.queue_ready := queue_readData.io.deq.ready
    io.queue_valid := queue_readData.io.deq.valid
    val unpacked_readData = queue_readData.io.deq.bits.data.asTypeOf(
        Vec(2, UInt(conf.Data_width.W)) // 0->size, 1->index
    )

    //for src_index_queue0 <> src_index_queue1 todo
    io.src_q1_enq.bits  <> io.src_q0_deq.bits
    io.src_q0_deq.ready := false.B // send
    io.src_q1_enq.valid := false.B // recieve

    switch(state) {
        is(read_rsp){
            when(io.to_arbiter.ready && queue_readData.io.deq.valid && io.src_q0_deq.valid && io.src_q1_enq.ready){
                when(unpacked_readData(0) > 256.U){ // split into multiple burst
                    when(((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))/4096.U =/=
                    ((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)+(256*(conf.HBM_Data_width / 8)).U-1.U)/4096.U){
                    // when burst cross 4k boundary
                        before_4k_bound_count := (((((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)) & 0xFFFFF000L.U) + 0x1000.U) - ((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))) / (conf.HBM_Data_width / 8).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := unpacked_readData(1) + C_offset
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := unpacked_readData(0) - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := unpacked_readData(1) + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{
                        io.to_arbiter.bits.index := unpacked_readData(1) + C_offset
                        io.to_arbiter.bits.burst_len := 255.U(8.W)
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        neighbour_count := unpacked_readData(0) - 256.U
                        burst_sum := burst_sum + 256.U
                        temp_index := unpacked_readData(1) + 256.U
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }
                }.elsewhen(unpacked_readData(0) =/= 0.U){ //0<neighbour<=256
                    when(((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))/4096.U =/=
                    ((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W) + (unpacked_readData(0) * (conf.HBM_Data_width / 8).U)-1.U)/4096.U){
                    // cross 4k boundary    
                        before_4k_bound_count := (((((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)) & 0xFFFFF000L.U) + 0x1000.U) - ((unpacked_readData(1) + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))) / (conf.HBM_Data_width / 8).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := unpacked_readData(1) + C_offset
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := unpacked_readData(0) - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := unpacked_readData(1) + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{        
                        io.to_arbiter.bits.index := unpacked_readData(1) + C_offset
                        io.to_arbiter.bits.burst_len := unpacked_readData(0) - 1.U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        burst_sum := burst_sum + unpacked_readData(0)
                        io.src_q0_deq.ready := true.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := true.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }
                } .otherwise{ // neighbour_num == 0
                    io.src_q0_deq.ready := true.B // send
                    io.src_q1_enq.valid := false.B  // recieve
                    queue_readData.io.deq.ready := true.B // send
                    io.to_arbiter.valid := false.B // recieve
                }
            }
        }
        is(loop){ // when burst size > 256
            when(io.to_arbiter.ready && io.src_q0_deq.valid && io.src_q1_enq.ready){
                when(neighbour_count > 256.U){
                    when(((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))/4096.U =/=
                    ((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)+(256*(conf.HBM_Data_width / 8)).U-1.U)/4096.U){
                    // cross 4k boundary    
                        before_4k_bound_count := (((((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)) & 0xFFFFF000L.U) + 0x1000.U) - ((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))) / (conf.HBM_Data_width / 8).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := temp_index + C_offset
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := neighbour_count - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := temp_index + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }. otherwise{                         
                        io.to_arbiter.bits.index := temp_index + C_offset
                        io.to_arbiter.bits.burst_len := 255.U(8.W)
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        temp_index := temp_index + 256.U
                        burst_sum := burst_sum + 256.U
                        neighbour_count := neighbour_count - 256.U
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                    }
                }.otherwise{ // <=256
                    when(((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))/4096.U =/=
                    ((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)+(neighbour_count*(conf.HBM_Data_width / 8).U)-1.U)/4096.U){
                    // cross 4k boundary    
                        before_4k_bound_count := (((((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W)) & 0xFFFFF000L.U) + 0x1000.U) - ((temp_index + C_offset)*(conf.HBM_Data_width / 8).asUInt(conf.Data_width.W))) / (conf.HBM_Data_width / 8).U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        io.to_arbiter.bits.index := temp_index + C_offset
                        io.to_arbiter.bits.burst_len := before_4k_bound_count - 1.U
                        neighbour_count := neighbour_count - before_4k_bound_count
                        burst_sum := burst_sum + before_4k_bound_count
                        temp_index := temp_index + before_4k_bound_count
                        io.src_q0_deq.ready := false.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := false.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := loop
                    }. otherwise{                     
                        io.to_arbiter.bits.index := temp_index + C_offset
                        io.to_arbiter.bits.burst_len := neighbour_count - 1.U
                        io.to_arbiter.bits.id := 1.U(conf.memIDBits.W)
                        burst_sum := burst_sum + neighbour_count
                        temp_index := 0.U
                        neighbour_count := 0.U
                        io.src_q0_deq.ready := true.B // send
                        io.src_q1_enq.valid := true.B  // recieve
                        queue_readData.io.deq.ready := true.B // send
                        io.to_arbiter.valid := true.B // recieve
                        state := read_rsp
                    }
                }
            }
        }
    }
}

class myArbiterIO(implicit val conf : HBMGraphConfiguration) extends Bundle {
    val index = UInt(conf.Data_width.W) // in 64 bits
    val burst_len = UInt(8.W) // in 64 bits
    val id = UInt(2.W) // 0->index, 1->neighbour
}


// Memory logic
// (read modified CSR: R indices followed by neighbour number)
class Memory(val num :Int)(implicit val conf : HBMGraphConfiguration) extends Module {
    val io = IO(new Bundle {
        // input
        val R_array_index = Vec(conf.pipe_num_per_channel, Flipped(Decoupled(UInt(conf.Data_width.W)))) 
        // input vertex index of the required CSR
        val write_vertex_index = Vec(conf.pipe_num_per_channel, Flipped(Decoupled(UInt(conf.Data_width.W)))) 
        // input vertex index you want to write
        val p1_end = Input(Bool())                                           // input p1_end
        val level = Input(UInt(conf.Data_width.W))                           // input level (constant in one iter)
        val push_or_pull_state = Input(Bool())                               // 0 for push
        val offsets = Input(new offsets)                                     // offsets
        val if_write = Input(Bool())                                         // if write
        val uram_out_a = Vec(conf.pipe_num_per_channel, Input(UInt(conf.Data_width_uram.W)))
        val uram_out_b = Vec(conf.pipe_num_per_channel, Input(UInt(conf.Data_width_uram.W)))
        //kernel count reg
        val kernel_count = Input(UInt(32.W))
        val master_finish = Input(Bool())
        val node_num = Input(UInt(conf.Data_width.W))

        // output
        val neighbour_cnt = Output(UInt(conf.Data_width.W))       // output neighbour count of the vertex
        val mem_end = Output(Bool())                              // output neighbour count of the vertex
        val neighbours = Vec(2*conf.pipe_num_per_channel, Decoupled(UInt((conf.Data_width*2).W)))
        // output 2*pipe_num_per_channel neighbours with src in local subgraph
        val uram_addr_a = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Addr_width_uram.W)))
        val uram_addr_b = Vec(conf.pipe_num_per_channel, Output(UInt(conf.Addr_width_uram.W)))
        val write_finish = Output(Bool())
        val HBM_interface = new AXIMasterIF(conf.HBM_Addr_width, conf.HBM_Data_width, conf.memIDBits) // HBM interface
    })
    dontTouch(io)

    // write module
    val write_channel = Module(new Memory_write(num))
    // write_channel.io.write_vertex_index <> io.write_vertex_index
    val write_index_arb = Module(new RRArbiter(UInt(conf.Data_width.W), conf.pipe_num_per_channel))
    for(i <- 0 until conf.pipe_num_per_channel){
        // write_vertex_index
        val tmp_q = Queue(io.write_vertex_index(i), conf.write_vertex_index_pre_len)
        tmp_q <> write_index_arb.io.in(i)

        // uram
        io.uram_addr_a <> write_channel.io.uram_addr_a
        io.uram_addr_b <> write_channel.io.uram_addr_b
        io.uram_out_a <> write_channel.io.uram_out_a
        io.uram_out_b <> write_channel.io.uram_out_b
    }
    write_index_arb.io.out <> write_channel.io.write_vertex_index
    write_channel.io.level <> io.level
    write_channel.io.offsets <> io.offsets
    write_channel.io.if_write <> io.if_write
    write_channel.io.HBM_write_interface.writeAddr <> io.HBM_interface.writeAddr
    write_channel.io.HBM_write_interface.writeData <> io.HBM_interface.writeData
    write_channel.io.HBM_write_interface.writeResp <> io.HBM_interface.writeResp
    write_channel.io.kernel_count := io.kernel_count
    write_channel.io.master_finish := io.master_finish
    write_channel.io.write_finish <> io.write_finish
    write_channel.io.node_num     <> io.node_num
    // modules
    val arb = Module(new Arbiter(new myArbiterIO, 2))
    val R_array_index_queue = Module(new Queue(UInt(conf.Data_width.W), conf.Mem_R_array_index_queue_len))
    val src_index_queue0 = Module(new Queue(UInt(conf.Data_width.W), conf.src_index_queue_len))
    val src_index_queue1 = Module(new Queue(UInt(conf.Data_width.W), conf.src_index_queue_len))
    val read_neighbour = Module(new Read_neighbour)

    //counters
    val neighbours_valid = Wire(Bool())
    val (count_val_i0, counterWrap_i0) = Counter(R_array_index_queue.io.enq.ready && R_array_index_queue.io.enq.valid, 2147483647)
    val (count_val_i1, counterWrap_i1) = Counter(read_neighbour.io.queue_ready && read_neighbour.io.queue_valid, 2147483647)
    val count_val_n0 = read_neighbour.io.count_val_n0
    val (count_val_n1, counterWrap_n1) = Counter(neighbours_valid, 2147483647)  //HBM req num

    val count_n_vec = Array.ofDim[UInt](2*conf.pipe_num_per_channel)
    val count_w_vec = Array.ofDim[Bool](2*conf.pipe_num_per_channel)
    // val (count_neighbour0, counterWrap_nei0) = Counter(io.neighbours(0).ready && io.neighbours(0).valid, 2147483647)
    // val (count_neighbour1, counterWrap_nei1) = Counter(io.neighbours(1).ready && io.neighbours(1).valid, 2147483647)
    for(n_id <- 0 until 2*conf.pipe_num_per_channel){
        val (tmp0, tmp1)  = Counter(io.neighbours(n_id).ready && io.neighbours(n_id).valid, 2147483647)
        count_n_vec(n_id) = tmp0
        count_w_vec(n_id) = tmp1
    }

    //io.neighbours cat
    val neighbour = Wire(Vec(2*conf.pipe_num_per_channel, UInt(conf.crossbar_data_width.W)))
    val src = Wire(Vec(2*conf.pipe_num_per_channel, UInt(conf.crossbar_data_width.W)))
    for(i <- 0 until 2*conf.pipe_num_per_channel){
        io.neighbours(i).bits := Cat(Fill((conf.Data_width - conf.crossbar_data_width) * 2, 0.U(1.W)),neighbour(i), src(i))
    }

    // neighbour_cnt
    io.neighbour_cnt := count_n_vec.reduce(_ + _)

    // mem_end
    io.mem_end := RegNext((count_val_i0 === count_val_i1) && (count_val_n0 === count_val_n1) && io.p1_end
        && io.HBM_interface.writeAddr.valid === false.B && io.HBM_interface.writeData.valid === false.B)

    // R_array_index <> R_array_index_arb
    val R_array_index_arb = Module(new RRArbiter(UInt(conf.Data_width.W), conf.pipe_num_per_channel))
    for(p1_id <- 0 until conf.pipe_num_per_channel){
        io.R_array_index(p1_id) <> R_array_index_arb.io.in(p1_id)
    }

    // R_array_index_arb <> R_array_index_queue
    R_array_index_arb.io.out <> R_array_index_queue.io.enq

    // R_array_index_queue <> arbiter & src_index_queue0
    arb.io.in(1).bits.index := (R_array_index_queue.io.deq.bits/(conf.channel_num).asUInt()) + Mux(io.push_or_pull_state, io.offsets.CSC_R_offset ,io.offsets.CSR_R_offset)
    arb.io.in(1).bits.burst_len := 0.U(8.W)
    arb.io.in(1).bits.id := 0.U(2.W)
    arb.io.in(1).valid := R_array_index_queue.io.deq.valid && src_index_queue0.io.enq.ready
    src_index_queue0.io.enq.valid := R_array_index_queue.io.deq.valid && arb.io.in(1).ready
    src_index_queue0.io.enq.bits <> R_array_index_queue.io.deq.bits

    R_array_index_queue.io.deq.ready := arb.io.in(1).ready && src_index_queue0.io.enq.ready

    // arbiter <> HBM_interface.readAddr
    val to_readAddr_queue = Queue(arb.io.out, conf.to_readAddr_queue_len)

    io.HBM_interface.readAddr.bits.addr <> (to_readAddr_queue.bits.index) * (conf.HBM_Data_width / 8).asUInt(conf.Data_width.W) + conf.HBM_base_addr * num.asUInt()
    io.HBM_interface.readAddr.bits.len <> to_readAddr_queue.bits.burst_len // assume <= 255
    io.HBM_interface.readAddr.bits.id <> to_readAddr_queue.bits.id  // 0->index, 1->neighbour
    io.HBM_interface.readAddr.ready <> to_readAddr_queue.ready
    io.HBM_interface.readAddr.valid <> to_readAddr_queue.valid

    //src_index_queue0 <> src_index_queue1
    src_index_queue0.io.deq <> read_neighbour.io.src_q0_deq
    src_index_queue1.io.enq <> read_neighbour.io.src_q1_enq

    // HBM_interface.readData <> read_neighbour
    io.HBM_interface.readData.bits     <> read_neighbour.io.readData.bits
    io.HBM_interface.readData.valid    <> read_neighbour.io.readData.valid

    // HBM_interface.readData & src_index_queue1 <> neighbours
    val unpacked_readData = io.HBM_interface.readData.bits.data.asTypeOf(
        Vec(2*conf.pipe_num_per_channel, UInt(conf.Data_width.W))
    )
    for(C_id <- 0 until 2*conf.pipe_num_per_channel){
        neighbour(C_id) := unpacked_readData(C_id)
        src(C_id) := src_index_queue1.io.deq.bits
    }
    src_index_queue1.io.deq.ready := io.HBM_interface.readData.bits.last && io.HBM_interface.readData.valid && (io.HBM_interface.readData.bits.id === 1.U) && io.HBM_interface.readData.ready

    // reduce neighbours_valid
    val fifo_ready_vec = Wire(Vec(2*conf.pipe_num_per_channel, Bool()))
    for(C_id <- 0 until 2*conf.pipe_num_per_channel){
        fifo_ready_vec(C_id) := io.neighbours(C_id).ready||io.neighbours(C_id).bits===(~(0.U(32.W)))
    }

    neighbours_valid := io.HBM_interface.readData.valid && io.HBM_interface.readData.bits.id === 1.U && 
        src_index_queue1.io.deq.valid && fifo_ready_vec.reduce(_&&_)
    for(C_id <- 0 until 2*conf.pipe_num_per_channel){
        io.neighbours(C_id).valid := neighbours_valid && (unpacked_readData(C_id) =/= ~(0.U(32.W)))
    }
    
    // HBM_interface.readData.ready: when one of the consumers ready
    io.HBM_interface.readData.ready := (read_neighbour.io.readData.ready && io.HBM_interface.readData.valid && io.HBM_interface.readData.bits.id === 0.U) || neighbours_valid

    // read_neighbour <> arbiter
    arb.io.in(0) <> read_neighbour.io.to_arbiter

    // read_neighbour <> offsets
    io.offsets <> read_neighbour.io.offsets
    io.push_or_pull_state <> read_neighbour.io.push_or_pull_state

}
