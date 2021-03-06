package com.sdc.graphx_example.test.unit

import org.junit.Test
import com.sdc.graphx_example.network._
import org.hamcrest.Matchers._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import org.slf4j.LoggerFactory

import com.sdc.graphx_example.shortestpath.single_source.ShortestPathSingleSourceForward
import com.sdc.graphx_example.shortestpath.single_source.VertexShortestPath
import org.apache.spark.graphx.Edge
import org.apache.spark.graphx.Graph
import com.sdc.graphx_example.shortestpath.custom_function.ShortestPathCustomCostFunction
import org.apache.spark.graphx.lib.ShortestPaths


@RunWith(classOf[BlockJUnit4ClassRunner])
class TestShortestPath extends TestWithSparkSession {
    
    val LOG = LoggerFactory.getLogger(classOf[TestShortestPath])

    
    def createLinks(nodes : List[com.sdc.graphx_example.network.Node]) = List(
        new Link(1, 1, 3, 20, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(2, 1, 2, 40, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(3, 3, 2, 10, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(4, 2, 5, 50, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(5, 3, 4, 30, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(6, 3, 6, 60, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(7, 2, 4, 10, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(8, 5, 4, 40, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(9, 4, 5, 20, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(10, 4, 6, 20, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(11, 5, 6, 50, 10, DrivingDirection.START2END.getValue, Array.empty)
        , new Link(12, 4, 6, 30, 10, DrivingDirection.START2END.getValue, Array.empty)
    )

    def createNodes() : List[Node] = List(Node(1, SimplePoint(1f, 1f)), Node(2, SimplePoint(3.0f, 0.0f)),
        Node(3, SimplePoint(3.0f, 3.0f)), Node(4, SimplePoint(5.0f, 0.0f)), Node(5, SimplePoint(5.0f, 3.0f)),
        Node(6, SimplePoint(7.0f, 1.0f)))
        
        
    private def testSP(spType :ShortestPathSingleSourceForward.COST_FUNCTION.Value, expected :Map[Long, VertexShortestPath]) {
        
        var nodes = createNodes()
        var links = createLinks(nodes)

        val nodesRdd = getSpark().sparkContext.parallelize(nodes)
        val vertices = nodesRdd.map(n => (n.getId, n))
        
        val linksRdd = getSpark().sparkContext.parallelize(links)
        val edges = linksRdd.map(l => Edge(l.getTail(), l.getHead(), l))
        
        val graphx = Graph(vertices, edges)

        
        LOG.info("####################################################")
        val source = 1l
        
        var spDistance = ShortestPathSingleSourceForward.run(graphx, source, spType)
        LOG.info("Shortest path custom (with arc cost function = %s)".format(spType))
        LOG.info("> Shortest path from %s result:".format(source))
        LOG.info(">> Vertices:")
        val spVertices = spDistance.vertices.collectAsMap()
        println(spVertices.mkString(System.lineSeparator()))
//            LOG.info(">> Edges:")
//            println(spDistance.edges.collect().mkString(System.lineSeparator()))
        
        for((k, v) <- expected){
            assertThat(spVertices.get(k).get.getMinCost(), is(equalTo(v.getMinCost())))
            assertThat(spVertices.get(k).get.getPredecessorLink(), is(equalTo(v.getPredecessorLink())))
        }
        
    }    
        
    @Test
    def testSPDistance(){
        testSP(ShortestPathSingleSourceForward.COST_FUNCTION.DISTANCE,
            Map(1l -> new VertexShortestPath(0.0,-1)
            , 2l -> new VertexShortestPath(30.0,3l)
            , 3l -> new VertexShortestPath(20.0,1l)
            , 4l -> new VertexShortestPath(40.0,7l)
            , 5l -> new VertexShortestPath(60.0,9l)
            , 6l -> new VertexShortestPath(60.0,10l)
            )
        )

    }
    
    @Test
    def testSPTravelTime(){
        
        testSP(ShortestPathSingleSourceForward.COST_FUNCTION.TRAVEL_TIME,
            Map(1l -> new VertexShortestPath(0.0,-1)
            , 2l -> new VertexShortestPath(3.0,3l)
            , 3l -> new VertexShortestPath(2.0,1l)
            , 4l -> new VertexShortestPath(4.0,7l)
            , 5l -> new VertexShortestPath(6.0,9l)
            , 6l -> new VertexShortestPath(6.0,10l)
            )
        )

    }
    
    @Test
    def testStandardSP(){
        
        
        var nodes = createNodes()
        var links = createLinks(nodes)

        val nodesRdd = getSpark().sparkContext.parallelize(nodes)
        val vertices = nodesRdd.map(n => (n.getId, n))
        
        val linksRdd = getSpark().sparkContext.parallelize(links)
        val edges = linksRdd.map(l => Edge(l.getTail(), l.getHead(), l))
        
        val graphx = Graph(vertices, edges)

        
        val sp = ShortestPaths.run(graphx, Seq(6l))
        
         val spVertices = sp.vertices.collectAsMap()
        println(spVertices.mkString(System.lineSeparator()))
       
        
    }
    
    
    @Test
    def testSPCustomFunction(){
        
        
        var nodes = createNodes()
        var links = createLinks(nodes)

        val nodesRdd = getSpark().sparkContext.parallelize(nodes)
        val vertices = nodesRdd.map(n => (n.getId, n))
        
        val linksRdd = getSpark().sparkContext.parallelize(links)
        val edges = linksRdd.map(l => Edge(l.getTail(), l.getHead(), l.length))
        
        val graphx = Graph(vertices, edges)

        val destId = 6l
        
        val sp = ShortestPathCustomCostFunction.run(graphx, Seq(destId))
        
        val spVertices = sp.vertices.collectAsMap()
        println(spVertices.mkString(System.lineSeparator()))
        
        val expected = Map(1l -> Map(destId -> 60.0f)
            , 2l -> Map(destId -> 30.0f)
            , 3l -> Map(destId -> 40.0f)
            , 4l -> Map(destId -> 20.0f)
            , 5l -> Map(destId -> 50.0f)
            , 6l -> Map(destId -> 0.0f))
        
        for((k, v) <- expected){
            assertThat(spVertices.get(k).get.get(destId).get, is(equalTo(v.get(destId).get)))
        }
        
    }
    
}