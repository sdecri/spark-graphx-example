package com.sdc.graphx_example.network

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.SQLContext
import com.vividsolutions.jts.geom.Point
import com.sdc.graphx_example.geometry.GeometryUtils
import org.apache.spark.sql.Row
import org.apache.spark.sql.Dataset
import org.slf4j.LoggerFactory
import scala.collection.immutable.List
import org.apache.spark.sql.functions._
import org.apache.spark.sql.Encoders

object GeoFunctions {

    private val LOG = LoggerFactory.getLogger(getClass)
    
    /**
     * Find the nearest node to the given source point among all the elements included in the specified dataframe
     * @param sourcePoint
     * @param nodesDF dataframe of nodes
     * @param session spark session
     * @param firstDistance first considered distance in which to search
     * @param nAttempt number of attempts. If no node is found the search continue considered a larger distance in which to search
     * @param extensionFactor factor used to enlarge the distance in which to search
     */
    def getNearestNode(sourcePoint : Point, nodesDS :Dataset[Node], session :SparkSession
            , firstDistance :Int = 1000, nAttempt :Int = 3, extensionFactor :Int = 10) : Option[Node] = {


        /**
         * A user defined function to compute the distance between the source point and each node in the dataset
         */
        def computeDistance(lon1 : Float, lat1 : Float) = udf((lon2 : Float, lat2 : Float) => GeometryUtils.getDistance(lon1, lat1, lon2, lat2))

        nodesDS.cache

        var nearestNodeTmp : Option[Node] = None
        var attempt = 0
        while (nearestNodeTmp.isEmpty && attempt < nAttempt) {
            val distanceBBox = firstDistance * ((attempt * extensionFactor) max 1)
            val ur = GeometryUtils.determineCoordinateInDistance(sourcePoint.getX, sourcePoint.getY, 45, distanceBBox)
            val bl = GeometryUtils.determineCoordinateInDistance(sourcePoint.getX, sourcePoint.getY, 45 + 180, distanceBBox)

            val distanceColumn = "distanceToPoint"
                        
            val nearestNodeList = 
                nodesDS.select("*")
                .where(col("%s.%s".format(Node.POINT, SimplePoint.LON)) <= ur.x && col("%s.%s".format(Node.POINT, SimplePoint.LON)) >= bl.x 
                        && col("%s.%s".format(Node.POINT, SimplePoint.LAT)) <= ur.y && col("%s.%s".format(Node.POINT, SimplePoint.LAT)) >= bl.y)
                .withColumn(distanceColumn, computeDistance(sourcePoint.getX.toFloat, sourcePoint.getY.toFloat)
                        (col("%s.%s".format(Node.POINT, SimplePoint.LON)), col("%s.%s".format(Node.POINT, SimplePoint.LAT))))
                .orderBy(asc(distanceColumn))
                .drop(col(distanceColumn))
                .as(Encoders.product[Node])
                .collect()
                
            if (!nearestNodeList.isEmpty)
                nearestNodeTmp = Some(nearestNodeList(0))

            attempt += 1
        }

        var nearestNode :Option[Node] = None
        if (nearestNodeTmp.isEmpty)
            LOG.warn("No nearest node found to the current source point: %s".format(sourcePoint))   
        else
            nearestNode = nearestNodeTmp 

        nearestNode
    }

}