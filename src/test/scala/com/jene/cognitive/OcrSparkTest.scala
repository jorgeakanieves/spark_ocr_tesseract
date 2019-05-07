package com.jene.cognitive

import java.io.FileWriter
import java.util.{Date, List, Properties}

import com.typesafe.scalalogging.slf4j.Logger
import org.apache.spark.{SparkConf, SparkContext}
import org.slf4j.LoggerFactory
import org.scalatest.FunSuite
import org.scalatest.BeforeAndAfter
import java.nio.file.{Files, Paths}
import java.text.SimpleDateFormat
import java.util

import org.apache.commons.lang.StringEscapeUtils

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import org.elasticsearch.spark._

class OcrSparkTest extends FunSuite with BeforeAndAfter {

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
  var sc:SparkContext = _
  val prop  = new Properties();

  before {
    var env = ""
    try {
      if (System.getProperty("env") != null )      env = System.getProperty("env")
      else env =  "local"
    } catch {
      case e: NullPointerException => println(e.getMessage());
        env =  "local"
    }
    val input = Thread.currentThread().getContextClassLoader().getResourceAsStream( "test."+env+".config.properties" );
    prop.load(input)

    val conf = new SparkConf().setAppName(this.getClass.getName).setMaster("local[*]")
    conf.set("es.nodes", prop.get("elk-node-port").toString)
    conf.set("es.mapping.id", "filename")

    sc = new SparkContext(conf)

    val inputOcrSpark = Thread.currentThread().getContextClassLoader().getResourceAsStream( "test."+env+".config.properties" );
    OcrSpark.prop.load(inputOcrSpark)

  }

  after {
    sc.stop()
  }

  test("test file extension") {

    //val rdd = sc.parallelize(("asfasf","saqsf"))
    val rddA = sc.binaryFiles (prop.get("path-pdfs").toString)
    val rddB = sc.binaryFiles (prop.get("path-pdfs2").toString)
    val rdd  = rddA.union(rddB).filter(OcrSpark.checkFileExtension)
    assert(rdd.count() == 2)
  }

  test("test conversion and ocr") {

    //val rdd = sc.parallelize(("asfasf","saqsf"))
    val broadcastprop = sc.broadcast(prop)
    val rdd  = sc.binaryFiles (prop.get("path-pdfs").toString)
      .map(OcrSpark.pdfToImageOcr(_,broadcastprop))
        .map(x=> {
          val it = x._2.iterator()
          var content = ""
          while(it.hasNext){
            val item = it.next()
            val text = item._2.toString
            content = content.concat(text)
          }
          (x._1, content)
        })

    // check a substring that exists in the test pdf file
    val res = (rdd.take(1)(0)._2.indexOf(prop.get("txt-to-find").toString) != -1)
    assert(res == true)

  }

/*
  test("test store fs") {

    //val rdd = sc.parallelize(("asfasf","saqsf"))
    val broadcastprop = sc.broadcast(prop)
    val rdd  = sc.binaryFiles (prop.get("path-pdfs").toString)
      .map(OcrSpark.pdfToImageOcr(_,broadcastprop))
          .map(OcrSpark.storeFs(_, broadcastprop))
            .count()

    //TODO: must have isolated methods, create below
/*
    val page1 = (1, "page1")
    val list:scala.collection.immutable.List[(Int, String)] = scala.collection.immutable.List(page1)
    //val list = List(page1)
    val listado = list.asJava
    val tup = (prop.get("path-pdfs").toString, listado)
    val data = Array(tup)
    val rdd2 = sc.parallelize(data)


    rdd2.map(OcrSpark.storeFs(_, broadcastprop))

rdd2.map(x=>{

  println(x._1.drop(6))
  var filecontent = ""
  val tuple = x._2.toList.sortBy(x=>x._1).map { x =>
    val text = x._2.toString
    filecontent = filecontent.concat(text)
  }
}).count()

    val filename = "\\scalaTest.pdf"
    val p = Paths.get(filename);
    val filepath = p.getFileName().toString();
    val filefolder = p.getParent().toString();
    val fileNewPath = prop.get("path-output").toString + filepath.toLowerCase().replace(".pdf", ".txt")
    val fw = new FileWriter(fileNewPath, true)
    println(fileNewPath)
    try {

      fw.write("zafasf")
    }
    finally fw.close()*/


    val exists = Files.exists(Paths.get(prop.get("path-output-file").toString))
    assert(exists == true)

  }
*/

  test("test create elk") {

    // prev erase reg in elk
    val tup = ((prop.get("path-pdfs").toString, prop.get("text-to-check").toString), prop.get("category-to-check").toString)
    val data = Array(tup)
    val rdd = sc.parallelize(data)
    OcrSpark.toElk(rdd)

    //TODO: Make a query by its content not for filename field
    val query = "{ \"query\" : { \"match\" : { \"filename\" : { \"query\": \"" + StringEscapeUtils.escapeJava(prop.get("path-pdfs").toString) + "\" } } } }"
    val rddR = sc.esRDD(prop.get("elk-index-docs").toString, Map[String, String]("es.read.field.include"->"filename,_id", "es.query" -> query))

    assert(rdd.count() == 1)

  }

  // mark that you want a test here in the future
  test ("test: removeFiles") (pending)

}
