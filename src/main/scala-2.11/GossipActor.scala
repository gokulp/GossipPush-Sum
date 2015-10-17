import akka.actor.{Actor, ActorRef}


import scala.collection.mutable.ArrayBuffer
import scala.math._
import scala.util.Random

import scala.concurrent.duration._

/**
 *
 * Created by gokul on 10/4/15.
 */
class GossipActor(nodes:Int, topology:String, initialSum:Double) extends Actor {
  import context._
  //Sum will be used for PushSum algorithm only
  var doAdminKnow:Boolean = false
  var sum:Double = initialSum
  var listenedCount = 0
  var weight:Double = 1.0
  var lastRatio:Double = sum/weight
  var ratioUnderThresholdSince:Int =0
  var ratio:Double = 0.0
  var order = 0
  var admin: ActorRef = null
  if(topology == "3D" || topology == "imp3D") order = ceil(cbrt(nodes)).toInt

  var neighbor = new ArrayBuffer[ActorRef]()

  def receive = {
    case Ciao(removeMe:ActorRef) =>
      //println("In " + self +" Removing " + removeMe)
      neighbor -= removeMe
      if (neighbor.length == 0 && !doAdminKnow) {
        admin ! Ciao(self)
        doAdminKnow = true
      }

    //Called for setting neighbors
    case DetectNeighbors(x, y, z) =>
      admin = sender()
      //println("Line:"+x+y+z)
      topology match {
        case "line" =>
          //println("Adding neighbors line")
          if ( x > 0 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x-1))            //set left  neighbor
          if ( x < (nodes -1) ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x+1))   //set right neighbor

        case "full" =>
          //println("Adding neighbors Full")
          for (j <- 0 until nodes){
            if (x != j){
              neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+j)
            }
          }

        case "3D" =>
          if (x > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x-1)+"x"+y+"y"+z)
          if (x < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x+1)+"x"+y+"y"+z)
          if (y > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y-1)+"y"+z)
          if (y < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y+1)+"y"+z)
          if (z > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z-1))
          if (z < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z+1))
          //println("Adding neighbors 3D"+x+y+z)
          //neighbor foreach{case a => println("N of "+x+y+z+" is "+a)}

        case "imp3D" =>
          //println("Adding neighbors imp3D")
          if (x > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x-1)+"x"+y+"y"+z)
          if (x < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+(x+1)+"x"+y+"y"+z)
          if (y > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y-1)+"y"+z)
          if (y < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+(y+1)+"y"+z)
          if (z > 0)         neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z-1))
          if (z < order -1 ) neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+x+"x"+y+"y"+(z+1))
          var randx = 0
          while (randx != x ) randx = Random.nextInt(order)
          var randy = 0
          while (randy != y ) randy = Random.nextInt(order)
          var randz = 0
          while (randz != z ) randz = Random.nextInt(order)
          neighbor += context.system.actorFor("akka://TOPOLOGY/user/admin/nodes"+randx+"x"+randy+"y"+randz)
      }

    case Rumor =>
      if (neighbor.isEmpty && !doAdminKnow){
        admin ! Ciao(self)
        doAdminKnow = true
      }
      if (listenedCount > 10 && !doAdminKnow) {
        admin ! Ciao(self)
        doAdminKnow = true
      } else if (listenedCount <= 10) {
        self ! PassOnRumor
        listenedCount += 1
      }

    case PassOnRumor =>
      if (neighbor.isEmpty && !doAdminKnow) {
        admin ! Ciao(self)
        doAdminKnow = true
      } else //if (!doAdminKnow)
      {
        if (neighbor.length > 0) neighbor(Random.nextInt(neighbor.length)) ! Rumor
        context.system.scheduler.scheduleOnce(500 milliseconds, self, PassOnRumor)
      }

    case PushSum(inSum, inWeight)=>
      if(!doAdminKnow) {
        sum = sum + inSum
        weight = weight + inWeight
        //println("Current Sum: "+sum+" Weight: "+weight)
        ratio = sum / weight
        if (Math.abs(ratio - lastRatio) < 0.0000000001) {
          ratioUnderThresholdSince += 1
        } else {
          ratioUnderThresholdSince = 0
        }
        lastRatio = ratio
        if (neighbor.isEmpty && !doAdminKnow) {
          //println("All neighbors down! Ciao!")
          admin ! Ciao(self)
          doAdminKnow = true
          //println("Converged Ratio for "+ initialSum + " at " + ratio)
          //context.stop(self)
        }
        if (ratioUnderThresholdSince > 2 && !doAdminKnow) {
          if (neighbor.length > 0) neighbor(Random.nextInt(neighbor.length)) ! PushSum(sum / 2, weight / 2)
          admin ! Ciao(self)
          doAdminKnow = true
          //println("Converged Ratio for "+ initialSum + " at " + ratio)
          //context.stop(self)
        } else if (!doAdminKnow) {
          //Node still not converged -- Pass the message along
          if (neighbor.length > 0) neighbor(Random.nextInt(neighbor.length)) ! PushSum(sum / 2, weight / 2)
          sum = sum / 2
          weight = weight / 2
          self ! PassOnPushSum
        }
      } else {
        sum = sum + inSum
        weight = weight + inWeight
        self ! PassOnPushSum
      }

    case PassOnPushSum =>
      if (neighbor.isEmpty && !doAdminKnow){
        //println("Converged Ratio for "+ initialSum + " at " + ratio)
        admin ! Ciao(self)
        doAdminKnow = true
        //context.stop(self)
      } else if (!doAdminKnow) {
        if (neighbor.length > 0) neighbor(Random.nextInt(neighbor.length)) ! PushSum(sum / 2, weight / 2)
        sum = sum / 2
        weight = weight / 2
        context.system.scheduler.scheduleOnce(500 milliseconds, self, PassOnPushSum)
      } else {
        if (neighbor.length > 0) neighbor(Random.nextInt(neighbor.length)) ! PushSum(sum / 2, weight / 2)
        sum = sum / 2
        weight = weight / 2
      }
  }
}

