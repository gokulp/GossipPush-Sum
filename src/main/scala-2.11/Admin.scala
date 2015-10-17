import akka.actor._
import scala.collection.mutable.ArrayBuffer
import scala.math._
import scala.concurrent.duration._
import scala.util.Random

/**
 * Created by gokul on 10/4/15.
 */

class Admin (nodes:Int, topology:String, algorithm:String) extends Actor {
  import context.dispatcher
  var order = 0
  if(topology == "3D" || topology == "imp3D") order = ceil(cbrt(nodes)).toInt

  var time = System.currentTimeMillis()
  if (nodes <= 1 )
    context.system.shutdown()

  var nodeRef = new ArrayBuffer[ActorRef]()        //Keeps track of ActorRef of all the nodes
  var lastConvergedNodes:Int = 0
  var terminatedNodeCount:Int = 0                  //Counts number of nodes terminated by Random Kill or exceptions
  var convergedNodes = new ArrayBuffer[ActorRef]() //Keeps track of converged Nodes
  var killTime:Int = 1000

  def watchActors(): Unit = {
    for (i <- nodeRef.indices) {
      context.watch(nodeRef(i))
    }
  }

  def createTopology() {
    //Creating a topology
    // Here each Actor will detect its own neighbor with case class Detect Neighbors
    topology match {
      case "line" =>
        println("Creating line topology!")
        var sumValue:Double = 1.0
        for (i <- 0 until nodes) {
          nodeRef += context.actorOf(Props(classOf[GossipActor], nodes, topology, sumValue), name = "nodes" + i) //Will work for line and full
          sumValue += 1
        }

        watchActors()
        for (i <- 0 until nodes) {
          nodeRef(i) ! DetectNeighbors(i, 0, 0)
        }

      case "full" =>
        println("Creating full topology")
        var sumValue:Double = 1.0
        for (i <- 0 until nodes) {
          nodeRef += context.actorOf(Props(classOf[GossipActor], nodes, topology, sumValue), name = "nodes" + i) //Will work for line and full
          sumValue += 1.0
        }
        watchActors()

        for (i <- 0 until nodes) {
          nodeRef(i) ! DetectNeighbors(i, 0, 0)
        }

      case "3D" =>
        var sumValue: Double = 1.0
        for (i <- 0 until order; j <- 0 until order; k <- 0 until order) {
          nodeRef += context.actorOf(Props(classOf[GossipActor], nodes, topology, sumValue), name = "nodes" + i+"x" + j+"y" + k) //Will work for line and full
          sumValue += 1.0
        }
        watchActors()
        var index:Int = 0
        for (i <- 0 until order; j <- 0 until order; k <- 0 until order) {
          nodeRef(index) ! DetectNeighbors(i, j, k)
          index += 1
        }

      case "imp3D" =>
        var sumValue: Double = 1.0
        for (i <- 0 until order; j <- 0 until order; k <- 0 until order) {
          nodeRef += context.actorOf(Props(classOf[GossipActor], nodes, topology, sumValue), name = "nodes" + i+"x" + j+"y" + k) //Will work for line and full
          sumValue += 1.0
        }
        watchActors()
        var index:Int = 0
        for (i <- 0 until order; j <- 0 until order; k <- 0 until order) {
          nodeRef(index) ! DetectNeighbors(i, j, k)
          index += 1
        }
    }
    println("Topology created!")
  }

  //Sends Message to neighbor in line structure
  def sendMessage(index:Int, node:ActorRef): Unit ={
    if (0 <= index || index < nodes) {
      var nameString: String = new String("akka://TOPOLOGY/user/admin/nodes" + index)
      //println(nameString)
      var actorRef = context.system.actorFor(nameString)
      actorRef ! Ciao(node)
    }
  }
  //Sends message to neighbors in 3D structure
  def sendMessage(x:Int, y:Int, z:Int, node:ActorRef): Unit ={
    var neighbor:ActorRef = null
    if (x > 0)         { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x-1)+"x"+y+"y"+z); neighbor ! Ciao(node);}
    if (x < order -1 ) { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x+1)+"x"+y+"y"+z); neighbor ! Ciao(node);}
    if (y > 0)         { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y-1)+"y"+z); neighbor ! Ciao(node);}
    if (y < order -1 ) { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y+1)+"y"+z); neighbor ! Ciao(node);}
    if (z > 0)         { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z-1)); neighbor ! Ciao(node);}
    if (z < order -1 ) { neighbor = context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z+1)); neighbor ! Ciao(node);}
  }

  //Informs neighbors of 'node' about its death
  def sendEulogy(node: ActorRef): Unit ={
    //node.
    topology match {
      case "line"=>
        var index:Int = node.path.name.substring(node.path.name.lastIndexOf('s')+1).toInt
        //println(index)
        sendMessage(index+1, node)
        sendMessage(index-1, node)

      case "full" =>
        for (i <- nodeRef.indices) {
          if (nodeRef(i) != node) {
            nodeRef(i) ! Ciao(node)
          }
        }

      case "3D" =>
        var x_index:Int = node.path.name.substring(node.path.name.lastIndexOf('s')+1,node.path.name.lastIndexOf('x')).toInt
        var y_index:Int = node.path.name.substring(node.path.name.lastIndexOf('x')+1,node.path.name.lastIndexOf('y')).toInt
        var z_index:Int = node.path.name.substring(node.path.name.lastIndexOf('y')+1).toInt
        //println("Indices :"+x_index+y_index+z_index)
        sendMessage(x_index,y_index,z_index, node)

      case "imp3D" =>
        var x_index:Int = node.path.name.substring(node.path.name.lastIndexOf('s')+1,node.path.name.lastIndexOf('x')).toInt
        var y_index:Int = node.path.name.substring(node.path.name.lastIndexOf('x')+1,node.path.name.lastIndexOf('y')).toInt
        var z_index:Int = node.path.name.substring(node.path.name.lastIndexOf('y')+1).toInt
        //println("Indices :"+x_index+y_index+z_index)
        sendMessage(x_index,y_index,z_index, node)
    }
  }
  def safeShutDown(): Unit ={
    for(i <- nodeRef.indices){
      nodeRef(i).tell( PoisonPill.getInstance, self)
    }
  }
  //Need to communicate to starting actor to start
  def receive = {
    case Terminated(node) =>
      //send eulogy to neighbors
      sendEulogy(node)
      nodeRef -= node
      terminatedNodeCount += 1
      //println("Actor "+node+" Terminated")

    case Ciao(node:ActorRef)=>
      //println("Ciao from actor "+node.path.name)
      if (convergedNodes.isEmpty) {
        //self ! RandomKill
        println("Send Message for killing a actor")
      }
      convergedNodes -= node
      convergedNodes += node
      if ((nodeRef.length - 1) < convergedNodes.length){
        safeShutDown()
        println("All actors done! Shutting down!")
        println("Time to converge is: "+(System.currentTimeMillis() - time)+" Milliseconds" )
        println("Total Nodes terminated: "+terminatedNodeCount)
        println("Total Nodes converged: "+convergedNodes.length)
        safeShutDown()
        context.system.shutdown()
        context.stop(self)
        System.exit(0)
      }

    case StartRumors =>
      var link:Int = 0
      if (topology== "line" || topology == "full") {
        link = (nodes + 1) / 2
      }
      else {
        link = pow((order+1)/2, 3).toInt
      }

      println("Starting node is "+link)
      createTopology()
      time = System.currentTimeMillis()
      if (algorithm == "gossip") nodeRef( link ) ! Rumor
      else if (algorithm == "push-sum") nodeRef(link) ! PushSum(0, 0)
      else {
        println ("Incorrect Algorithm!\n")
        System.exit(2)
      }
      context.system.scheduler.scheduleOnce(5000 milliseconds, self, CheckForConvergence)

    case CheckForConvergence =>
      if (convergedNodes.length > lastConvergedNodes){
        lastConvergedNodes = convergedNodes.length
        println("Converged node count at "+(System.currentTimeMillis() - time)+" Milliseconds is "+lastConvergedNodes )
        context.system.scheduler.scheduleOnce(5000 milliseconds, self, CheckForConvergence)
      } else {
        println("Converged "+lastConvergedNodes+" nodes! Timeout Occured in Main process!")
        println("Time to converge is: "+(System.currentTimeMillis() - time)+" Milliseconds" )
        println("Total Nodes terminated: "+terminatedNodeCount)
        println("Total Nodes converged: "+convergedNodes.length)
        safeShutDown()
        context.system.shutdown()
        context.stop(self)
        System.exit(0)
      }

    case RandomKill =>
      var num:Int = Random.nextInt(nodes)
      println("Killing actor "+num)
      nodeRef(num).tell(PoisonPill.getInstance, self)
      context.system.scheduler.scheduleOnce(killTime milliseconds, self, RandomKill)
  }
}

