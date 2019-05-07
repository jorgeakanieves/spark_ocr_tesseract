package com.jene.cognitive

import java.awt.Image
import java.awt.image.{BufferedImage, DataBufferByte, RenderedImage}
import java.io._
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util
import java.util.{Date, List, Properties, UUID}

import com.esotericsoftware.kryo.io.{Input, Output}
import com.esotericsoftware.kryo.{Kryo, KryoSerializable}
import com.typesafe.scalalogging.slf4j.Logger
import net.sourceforge.tess4j._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.compress.GzipCodec
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{SerializableWritable, SparkConf, SparkContext, SparkFiles}
import org.elasticsearch.spark._
import org.ghost4j.GhostscriptException
import org.ghost4j.document.PDFDocument
import org.ghost4j.renderer.{RendererException, SimpleRenderer}
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._
import scala.collection.immutable.HashMap
import scala.util.Try


object OcrSpark extends KryoSerializable{

  val logger = Logger(LoggerFactory.getLogger(this.getClass.getName))
  val prop  = new Properties();
  //val props = Map[String,String]();

  def main(args: Array[String]) {

    var configFile = ""
    try {
      if (System.getProperty("env") != null )      configFile = System.getProperty("env") + ".config.properties"
      else configFile =  "local.config.properties"
    } catch {
      case e: NullPointerException => println(e.getMessage());
        configFile =  "local.config.properties"
    }

    val input = Thread.currentThread().getContextClassLoader().getResourceAsStream( configFile );
    prop.load(input)

    if( Try(prop.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> Init " + this.getClass.getName + " app")

    if( Try(prop.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> Loaded " + configFile)

    for (e <- prop.entrySet) {
      if( Try(prop.get("logging").toString.toBoolean).getOrElse(true)  )
        logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> property key: " + e.getKey.toString + " -> value: " + e.getValue.toString)
      //props += (e.getKey.toString -> e.getValue.toString)
    }

    //System.setProperty("hadoop.home.dir", "C:\\java\\tools\\hadoop_winutils");
    //System.setProperty("TESSDATA_PREFIX", "/apps/jene/tesseract/tessdata/");
    //System.loadLibrary("/usr/local/lib/libtesseract.so")
    //System.loadLibrary("/usr/local/lib/liblept.so.4")


    val conf = new SparkConf().setAppName(this.getClass.getName)
    conf.set("es.nodes", prop.get("elk-node-port").toString)
    conf.set("es.mapping.id", "filename")
    conf.set("es.read.field.empty.as.null", "false")
    conf.set("es.field.read.empty.as.null", "false")
    conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")

    conf.set("spark.network.timeout", "6000")
    conf.set("spark.rpc.askTimeout", "6000")
    conf.set("spark.akka.timeout", "6000")
    conf.set("spark.worker.timeout", "6000")
    // for local test
    if(System.getProperty("env") == null  || System.getProperty("env") == "local")
      conf.setMaster("local[*]")

    val sc = new SparkContext(conf)
    sc.hadoopConfiguration.set("mapreduce.input.fileinputformat.input.dir.recursive","true")

    val broadcastProps = sc.broadcast(prop)

    /**
      * STEP 1: pdf -> img + ocr
      */
    val ocrRdd = sc.binaryFiles (prop.get("path-pdfs").toString, prop.get("spark-partitions").toString.toInt)
      .filter( checkFileExtension )
      .map{ pdfToImageOcr(_, broadcastProps)}
      .map { storeFs(_, broadcastProps)}
      .persist(StorageLevel.MEMORY_ONLY)



    /**
      * STEP 2: classification
      */
    // send doc in only a line
    val catRdd =
      ocrRdd.map(x => x._2.replace("\n", ""))
        .pipe(Seq(prop.get("classificator-app-path").toString),Map("SEPARATOR" -> ","))
        .filter(l=>l!="")
        .persist(StorageLevel.MEMORY_ONLY)


    val fullRdd = ocrRdd.zip(catRdd)

    /**
      * STEP 3: save to elk
      */
    toElk(fullRdd)

    /**
      * STEP 4: save to hdfs
      */
    fullRdd.saveAsTextFile(prop.get("path-pdfs-saved").toString+"/"+UUID.randomUUID().toString(), classOf[GzipCodec])


    /**
      * STEP 5: remove files from origin (hdfs)
      */
    //TODO: validate process checking each file has been indexed (to elk)
    val hadoopConf = new SerializableWritable(sc.hadoopConfiguration)
    ocrRdd.map(x=>x._1).map(removeFiles(_, broadcastProps, hadoopConf)).count()

    ocrRdd.unpersist()
    fullRdd.unpersist()

    sc.stop()
  }

  /**
    * Check valid file extension (PDF)
    *
    * @param file
    * @return
    */
  def checkFileExtension(file: (String, org.apache.spark.input.PortableDataStream) ): Boolean=
  {
    val i = file._1.lastIndexOf('.');
    val extension = file._1.substring(i+1);
    if("pdf".equalsIgnoreCase(extension)) return true
    else return false
  }

  /**
    * Method to convert a pdf file to png images
    *
    * @param file (filename, pdfdocument)
    * @return
    */
  def pdfToImageOcr (
                      file: (String, org.apache.spark.input.PortableDataStream),
                      prop: Broadcast[Properties] )
  : (String, List[(Int, String)]) = synchronized
  {
    /** Render the PDF into a list of images with 300 dpi resolution
      * One image per PDF page, a PDF document may have multiple pages
      */

    val properties = prop.value;

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> init convert")

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start load pdf "+ file._1)


    val document: PDFDocument = new PDFDocument( );
    try {
      document.load(file._2.open)
    } catch {
      case e: Exception => if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
        logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> error document not valid " + file._1 + " ex: " + e.getMessage())
    }

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end load pdf " + file._1)

    //Ghostscript.getInstance();
    val renderer :SimpleRenderer = new SimpleRenderer( )

    var config = Map[String,String]();
    config += (
      "-dBATCH" -> "",
      "-dQUIET"-> "",
      "-dNOPAUSE"-> "",
      "-dSAFER"-> "",
      "-dGraphicsAlphaBits" -> "4",
      "-dAlignToPixels" -> "0",
      "-dPDFFitPage" -> "",
      "-sDEVICE" -> "pnggray",
      "-sProcessColorModel" -> "DeviceGray",
      "-sColorConversionStrategy" -> "Gray",
      "-dOverrideICC" -> "-r200",
      "-dNOGC" -> "")

    renderer.copySettings(config)
    //renderer.setMaxProcessCount(0);
    renderer.setResolution( 200 )

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start render pdf " + file._1)

    // tactic split rendered
    val numberOfPages = document.getPageCount();
    val maxPags = properties.get("max-pags-pdf").toString.toInt;
    var textList:List[(Int, String)] = new util.ArrayList();

    // render all pages from document
    if (numberOfPages <= maxPags) {

      val images:List[Image] = renderer.render(document);
      textList = ocr(images, properties, file._1, 1)

    // issue heap size object on heavy docs
    } else {

      val im = maxPags - 1;
      val round = Math.ceil( numberOfPages / maxPags);

      var pagination:Int = 0
      for(i <- scala.List.range(0, round.toInt)) {
        pagination = ((i * maxPags) + maxPags)
        if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
          logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> loop pages from "+ ((i * maxPags)+1) + " to " + pagination)

        try {
          val images:List[Image] = renderer.render(document, i * maxPags, i * maxPags + im)
          textList.addAll(ocr(images, properties, file._1, i * maxPags))

        } catch {
          case e: RendererException => if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
            logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> error render " + e.getMessage())
        }
      }

      if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
        logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> final pages from "+ pagination + " to " + numberOfPages)

      try {
        val images:List[Image] = renderer.render(document, pagination, numberOfPages - 1)
        textList.addAll(ocr(images, properties, file._1, pagination))

      } catch {
        case e: RendererException => if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
          logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> error render " + e.getMessage())
      }
    }


    (file._1, textList)
  }


  /**
    *
    * @param images
    * @param props
    * @param filename
    * @return
    */
  def ocr (
            images : java.util.List[Image], props : Properties, filename : String, initPage : Int )
  : List[(Int, String)] =
  {

    val properties = props;

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> init ocr: " + filename)

    // instance class ocr
    val instance = new Tesseract();  // JNA Interface Mapping
    instance.setLanguage("spa");
    instance.setDatapath(properties.get("tesseract-data").toString);
    //instance.setDatapath("/usr/local/share/tessdata/");

    val params = java.util.Arrays.asList(
      "logfile","quiet",
      "textord_noise_normratio", "5",
      "image_default_resolution", "300"
    )
    instance.setConfigs(params);
    // silent
    //instance.setConfigs(java.util.Arrays.asList("logfile","quiet"));
    instance.initOcr()

    var i = initPage
    val textPagedList: java.util.List[(Int, String)] = images.toList.map { image =>
      i = i+1

      if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
        logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start buffered image " + i + " " + filename)

      // Create a buffered image with transparency
      val bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_BYTE_GRAY);
      // Draw the image on to the buffered image
      val bGr = bimage.createGraphics();
      bGr.drawImage(image, 0, 0, null);
      bGr.dispose();

      if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
        logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end buffered image " + i + " " + filename)

      val tuple: (Int, String) = null

      try {

        if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
          logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start ocr tesseract " + i + " " + filename)

        val result = instance.doOCR(bimage);

        if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
          logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end ocr tesseract " + i + " " + filename)
        (i, result)

      } catch{
        case e: TesseractException => println(e.getMessage());
          (i, "")
      } finally {
        (i, "")
      }
    }

    // finally end ocr
    instance.endOcr()

    textPagedList
  }



  /**
    * Method to store data on disk
    *
    * @param file (filename, list[ (page, content)])
    * @return
    */
  def storeFs (
                file: (String, List[(Int, String)]),
                prop: Broadcast[Properties] )
  : (String, String) =
  {

    val properties = prop.value;

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> init fs store")

    var filecontent = ""
    var page = 1

    val filename = file._1.drop(6)
    //
    val p = Paths.get(filename);
    val filepath = p.getFileName().toString();
    val filefolder = p.getParent().toString();
    val fileNewPath = properties.get("path-output").toString + filepath.toLowerCase().replace(".pdf", ".txt")

    val tuple = file._2.toList.sortBy(x=>x._1).map { x =>
      val text = x._2.toString + "\n\n\t\t\t\t\t\t########### "+page+" ###########\n\n"
      page+=1
      filecontent = filecontent.concat(text)
    }

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> sample text content: " + filecontent.substring(0,200))

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start filewriter ")

    // first remove file if exists
    val f = new File(fileNewPath);
    f.delete()
    val fw = new FileWriter(fileNewPath, true)

    try {
      filecontent = filecontent.concat(filecontent)
      fw.write(filecontent)
    }
    finally fw.close()

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end filewriter ")

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end fs store ")

    (file._1, filecontent)
  }


  /**
    * Method to save to elk index
    *
    * @param rdd (filename, text content)
    */
  def toElk (
              rdd : RDD[((String, String), (String))]
            )
  {

    if( Try(prop.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start toElk")

    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    val date = new Date();
    rdd.map(
      line =>
        HashMap(
          "filename" -> line._1._1,
          "classification" -> line._2,
          "date" -> dateFormat.format(date),
          "content" -> line._1._2.substring(0,300))
    ).saveToEs(prop.get("elk-index-docs").toString)

    if( Try(prop.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end toElk")
  }


  /**
    * Method to store data on disk
    *
    * @param file (filename, list[ (page, content)])
    * @return
    */
  def removeFiles (
                    file: String,
                    prop: Broadcast[Properties],
                    conf:   SerializableWritable[org.apache.hadoop.conf.Configuration]
                  )
  : Unit =
  {

    val properties = prop.value;

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> init removeFiles")

    val pdfFile = file

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> removing file : "+ pdfFile)

    val fs = org.apache.hadoop.fs.FileSystem.get(conf.value);
    if(fs.exists(new org.apache.hadoop.fs.Path(pdfFile)))
      fs.delete(new org.apache.hadoop.fs.Path(pdfFile), true); // delete file, true for recursive

    if( Try(properties.get("logging").toString.toBoolean).getOrElse(true)  )
      logger.info(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end removeFiles")

  }

  override def write(kryo: Kryo, output: Output): Unit = ???

  override def read(kryo: Kryo, input: Input): Unit = ???
}