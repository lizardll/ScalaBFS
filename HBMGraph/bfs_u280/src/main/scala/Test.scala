package HBMGraph
import chisel3._
import chisel3.Driver
import chisel3.util._
import chisel3.iotesters.PeekPokeTester


object Testp1 extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new P1(0)(configuration))
  
}


object Testp2 extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new P2(0)(configuration))
  
}


object TestMem_write extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Memory_write(0)(configuration))
  
}

object TestMemory extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Memory(0)(configuration))
  
}

object Testread_visited_map extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new p2_read_visited_map_or_frontier(0)(configuration))
  
}

object Testwrite_frontier_and_level extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new write_frontier_and_level(0)(configuration))
  
}

object Testfrontier extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new Frontier(0)(configuration))
  
}

object Testmaster extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new master()(configuration))
  
}

// class Test(c:Top) extends PeekPokeTester(c){
//   for(t <- 0 until 50){
//     println("-----------------------------------------------------------------------------------------------")
//     step(1)
//   }
// }


// object bfsTester {
//   def main(args: Array[String]): Unit = {
//     println("Testing bfs")
//     implicit val configuration = HBMGraphConfiguration()
//     iotesters.Driver.execute(Array[String](), () => new Top()) {
//       c => new Test(c)
//     }
//   }
// }

/*object Testbram extends App{
    implicit val configuration = HBMGraphConfiguration()
    chisel3.Driver.execute(Array[String](), () => new bram_top)
}*/

  