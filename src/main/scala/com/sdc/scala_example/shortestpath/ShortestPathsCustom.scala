package com.sdc.scala_example.shortestpath

import scala.reflect.ClassTag

import org.apache.spark.graphx._
import com.sdc.scala_example.network.Link
import org.slf4j.LoggerFactory

/**
 * Computes shortest paths from one source vertex to all vertices, returning a graph where each
 * vertex attribute is a map containing the:<br>
 * <ul>
 * <li> shortest-path distance.
 * </ul>
 */
object ShortestPathsCustom extends Serializable {
    
    private val LOG = LoggerFactory.getLogger(getClass)    
    
    object COST_FUNCTION extends Enumeration {
        var DISTANCE,TRAVEL_TIME = Value
        
        def fromValue(value :String) :COST_FUNCTION.Value = {
            if(value.equalsIgnoreCase(DISTANCE.toString())) DISTANCE
            else if(value.equalsIgnoreCase(DISTANCE.toString())) TRAVEL_TIME
            else
                throw new IllegalArgumentException("Not valid cost function type: %s. Available values: %s"
                        .format(value, COST_FUNCTION.values))
        }
    }
    
    private val INITIAL_COST = Double.PositiveInfinity

    private def createInitialMessage() : ShortestPathMessage = new ShortestPathMessage(INITIAL_COST, -1)

    /**
     * 
     */
    def run[VD](graph: Graph[VD, Link], source :VertexId, costFunctionType :COST_FUNCTION.Value = COST_FUNCTION.DISTANCE)
    : Graph[VertexShortestPath, Link] = {
        
        LOG.info("Run shortest path algorithm with cost function = %s".format(costFunctionType))
        
        val spGraph = graph.mapVertices { (vid, vertex) =>
            if (vid == source) 
                new VertexShortestPath(0, -1) 
            else 
                new VertexShortestPath(INITIAL_COST, -1)
        }

        val initialMessage = createInitialMessage()

        def vertexProgram(id: VertexId, vertex: VertexShortestPath, msg: ShortestPathMessage)
        : VertexShortestPath = {
            var newVertex = vertex 
            if (msg.getCostFromSource() < vertex.getMinCost()) {
                newVertex = new VertexShortestPath(msg.getCostFromSource(), msg.getPredecessorLink())
            }  
            return newVertex
        }

        def getLinkCost(link :Link) :Double = {
            var cost :Double = 0
            if (costFunctionType == COST_FUNCTION.DISTANCE)
                cost = link.getLength()
            else {
                cost = link.getTravelTime()
            }
            cost
        }
        
        def sendMessage(triplet: EdgeTriplet[VertexShortestPath, Link]): Iterator[(VertexId, ShortestPathMessage)] = {
            
            val linkCost :Double = getLinkCost(triplet.attr)
            val newDestCost = triplet.srcAttr.getMinCost() + linkCost
            if(newDestCost < triplet.dstAttr.getMinCost()){
                return Iterator((triplet.dstId, new ShortestPathMessage(newDestCost, triplet.attr.getId())))
            }
            else
                return Iterator.empty
        }
        
        def mergeMessage(msg1 :ShortestPathMessage, msg2: ShortestPathMessage) : ShortestPathMessage =
            if(msg1.getCostFromSource() < msg2.getCostFromSource())
                msg1
            else
                msg2

        Pregel(spGraph, initialMessage, activeDirection = EdgeDirection.Out)(vertexProgram, sendMessage, mergeMessage)

        
    }
}