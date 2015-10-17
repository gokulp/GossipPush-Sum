import akka.actor.ActorRef

/**
 * Created by gokul on 10/4/15.
 */
case class DetectNeighbors(x:Int, y:Int, z:Int )
case object Rumor
case class Ciao(removeMe:ActorRef)
case object PassOnRumor
case object StartRumors
case class PushSum(inSum: Double, inWeight: Double)
case object PassOnPushSum
case object CheckForConvergence
case object RandomKill
