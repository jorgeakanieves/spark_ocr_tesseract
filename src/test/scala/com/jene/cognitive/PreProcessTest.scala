package com.jene.cognitive

import java.awt.Image
import java.awt.image.BufferedImage
import java.util
import java.util.Properties
import javax.imageio.ImageIO

import com.typesafe.scalalogging.slf4j.Logger
import org.apache.commons.lang.StringEscapeUtils
import org.apache.spark.{SparkConf, SparkContext}
import org.bytedeco.javacv.{Java2DFrameConverter, OpenCVFrameConverter}
import org.elasticsearch.spark._
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.slf4j.LoggerFactory

class PreProcessTest extends FunSuite with BeforeAndAfter {

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
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
  }

  after {

  }

  test("test full image preprocessed") {

    val processedImages = new util.ArrayList[Image]

    val pathToFile = new java.io.File(prop.get("test-image-path").toString);
    val image = ImageIO.read(pathToFile);

    // Draw the image on to the buffered image
    val bGr = image.createGraphics();
    bGr.drawImage(image, 0, 0, null);
    bGr.dispose();


    val im = image.asInstanceOf[Image]
    processedImages.add(image)
    val images = PreProcess.process(processedImages)
    val it = images.iterator()
    while(it.hasNext){
      val im = it.next()
      assert(im.getHeight(null) > 0 && im.getWidth(null) > 0 )
    }
  }

  test("test image convert") {

    val pathToFile = new java.io.File(prop.get("test-image-path").toString);
    val image = ImageIO.read(pathToFile)

    // Create a buffered image with transparency
    val bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_BYTE_GRAY);
    // Draw the image on to the buffered image
    val bGr = bimage.createGraphics();
    bGr.drawImage(image, 0, 0, null);
    bGr.dispose();

    val cv = new OpenCVFrameConverter.ToIplImage();
    val jcv = new Java2DFrameConverter();
    val matImage = cv.convertToMat(jcv.convert(bimage));
    val processed = PreProcess.convert(matImage)
    PreProcess.showImage(processed)
    assert(processed.size().height()>0 && processed.size().width()>0 )

  }
}
