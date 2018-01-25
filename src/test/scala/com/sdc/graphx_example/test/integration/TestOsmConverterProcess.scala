package com.sdc.graphx_example.test.integration

import org.junit.Test
import com.sdc.graphx_example.network._
import org.hamcrest.Matchers._
import org.junit.Assert._
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner
import java.io.File
import com.sdc.graphx_example.command_line.RUN_TYPE
import com.sdc.graphx_example.App
import org.apache.spark.sql.Row
import java.util.stream.Collectors
import scala.collection.mutable.WrappedArray
import java.util.Arrays
import com.sdc.graphx_example.test.unit.TestWithSparkSession


@RunWith(classOf[BlockJUnit4ClassRunner])
class TestOsmConverterProcess extends TestWithSparkSession{
    
    @Test
    def testProcess() = {

        val session = getSpark()
        import session.sqlContext.implicits._
        
        
        
        // clean output directory
        val outputDir : String = "target/test/integration/osm_converter/testProcess/"
        deleteDirectory(outputDir)
        val outputDirFile = new File(outputDir)
        println("Output dir clean? %s".format(!outputDirFile.exists()))

        val fileResourceNodes = "/networks/osm/casal-bertone/casal-bertone-node.parquet";
        val fileUrlNodes = this.getClass().getResource(fileResourceNodes);

        val fileResourceWays = "/networks/osm/casal-bertone/casal-bertone-way.parquet";
        val fileUrlWays = this.getClass().getResource(fileResourceWays);

        val linksRepartition = 3
        val nodesRepartition = 1
        
        val args = ("--spark-master local --run-type %s --osm-nodes-file %s --osm-ways-file %s" + 
            " --osmc-links-repartition-output %d --osmc-nodes-repartition-output %d --output-dir %s" +
            " --osmc-persist-links true")
            .format(RUN_TYPE.OSM_CONVERTER.getValue, fileUrlNodes, fileUrlWays, linksRepartition, nodesRepartition, outputDir)

        App.main(args.split(" "))
        
        //here the spark session has been closed
        getOrCreateSparkSession()

        val expectedNodesFile = new File(outputDir + "nodes")
        assertTrue(expectedNodesFile.exists())
        assertTrue(expectedNodesFile.isDirectory())

        val nodesDF = getSpark().read.json(expectedNodesFile.getAbsolutePath).orderBy("id")
        nodesDF.cache()
        nodesDF.show()
        assertTrue(nodesDF.count() > 0)

        val expectedLinksFile = new File(outputDir + "links")
        assertTrue(expectedLinksFile.exists())
        assertTrue(expectedLinksFile.isDirectory())

        val linkParquetFiles = expectedLinksFile.listFiles()
        val outputFiles = linkParquetFiles.filter(f => f.getPath().endsWith(".json"));
        assertEquals(linksRepartition, outputFiles.length);

        val linksDF = getSpark().read.json(expectedLinksFile.getAbsolutePath)
        linksDF.cache
        linksDF.printSchema()
        linksDF.show()
        assertTrue(linksDF.count() > 0)
        
        var actual = linksDF.select("*").where($"tail" === 957255602 && $"head" === 957254675).count()
        assertThat(actual, is(equalTo(1l)))
        
        var selection = linksDF.select("points").where($"tail" === 957255602 && $"head" === 957254675)
        .map((row:Row) => {
            
            val pointsRaw = row.getAs[WrappedArray[Row]](0)

            val points = pointsRaw
                .map(r => SimplePoint(r.getDouble(0).toFloat, r.getDouble(1).toFloat)).toArray

            points
            
        }).collectAsList().get(0)
        
        selection.foreach(println)
        assertThat(selection.size, is(equalTo(2)))
        
        actual = linksDF.select("*").where($"tail" === 957254675 && $"head" === 957255602).count()
        assertThat(actual, is(equalTo(0l)))
        
        actual = linksDF.select("*").where($"tail" === 295781343 && $"head" === 296057855).count()
        assertThat(actual, is(equalTo(1l)))
        
        actual = linksDF.select("*").where($"tail" === 296057855 && $"head" === 295781343).count()
        assertThat(actual, is(equalTo(1l)))
        
        actual = linksDF.select("*").where($"tail" === 295778585 && $"head" === 295780015).count()
        assertThat(actual, is(equalTo(1l)))
        
        actual = linksDF.select("*").where($"tail" === 295780015 && $"head" === 295778585).count()
        assertThat(actual, is(equalTo(0l)))        
        
    }



}