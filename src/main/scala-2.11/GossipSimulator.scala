import akka.actor.{Props, ActorSystem}

import scala.math._


/**
 * Created by gokul on 10/4/15.
 */
object GossipSimulator extends App {
  override def main(args: Array[String]): Unit ={
    var root = 0
    if(args.length != 3){
      println("Please enter 3 arguments in order\n \t 1. Number of Nodes\n \t 2. Topology\n \t 3. Algorithm")
      System.exit(1)
    }

    var nodes:Int = args(0).toInt
    var topology:String = args(1)
    var algorithm:String = args(2)

    //for 3D structures number of nodes will always be perfect cube!
    if(topology == "3D" || topology == "imp3D"){
      root = ceil(cbrt(nodes)).toInt
      nodes = pow(root, 3).toInt
    }

    val system = ActorSystem("TOPOLOGY")
    val admin = system.actorOf(Props(classOf[Admin], nodes, topology, algorithm), name = "admin")

    admin ! StartRumors

  }
}
