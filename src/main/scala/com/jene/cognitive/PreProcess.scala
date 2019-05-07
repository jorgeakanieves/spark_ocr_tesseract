package com.jene.cognitive

import java.awt.Image
import java.awt.image.BufferedImage
import java.util
import javax.swing.JFrame

import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgproc._
import org.bytedeco.javacv.{CanvasFrame, Java2DFrameConverter, OpenCVFrameConverter}



object PreProcess {

  def process (images : java.util.List[Image]) : java.util.List[Image]  = {

    val it = images.iterator()
    val processedImages= new util.ArrayList[Image]

    while(it.hasNext){
      val image = it.next()

      // Create a buffered image with transparency
      val bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_BYTE_GRAY);
      // Draw the image on to the buffered image
      val bGr = bimage.createGraphics();
      bGr.drawImage(image, 0, 0, null);
      bGr.dispose();

      val cv = new OpenCVFrameConverter.ToIplImage();
      val jcv = new Java2DFrameConverter();
      val matImage = cv.convertToMat(jcv.convert(bimage));
      val processed = convert(matImage)
      val im = jcv.convert(cv.convert(processed))
      val imageFinal = im.asInstanceOf[Image]

      processedImages.add(imageFinal)

    }

    processedImages
  }


  def convert(originalImage: Mat) : Mat = {

    //var imageColor = org.bytedeco.javacpp.opencv_imgcodecs.imread("c:\\tmp\\test.jpg", IMREAD_COLOR)

    var image = new Mat()

    // Color conversion accelerates tesseract processing time
    //cvtColor(originalImage, image,  org.bytedeco.javacpp.opencv_imgproc.COLOR_BGR2GRAY)
    //cvtColor(originalImage, image,  CV_RGB2GRAY)
    image = originalImage

    //showImage(image, "1. BW Image")

    // Binarize for error reduction
    var mask = new Mat()
    threshold(image, mask, 100, 255, THRESH_BINARY_INV)
    //showImage(mask, "2. Inverse Image")

    var kernel3 = getStructuringElement(MORPH_RECT, new Size(3, 3))
    erode(mask, mask, kernel3)
    //showImage(mask, "3. Eroded Image")

    var kernel100 = getStructuringElement(MORPH_RECT, new Size(100, 100))
    dilate(mask, mask, kernel100)
    //showImage(mask, "4. Dilated mask")

    var kernel50 = getStructuringElement(MORPH_RECT, new Size(50, 50))
    erode(mask, mask, kernel50)
    //showImage(mask, "5. ReEroded mask")

    // Try to rotate image
    var dots = new Mat()
    findNonZero(mask, dots)

    // Prepare a white GRAY image
    var clean = new Mat(image.size(), image.`type`())
    bitwise_not(clean, clean)

    // Copy Original GRAY image using the mask
    image.copyTo(clean, mask)
    //showImage(clean, "6. Clean Image")

    // Only Important part
    var roi = boundingRect(mask)
    var small = clean.apply(roi)

    // Reduce to half Size
    var rx = small.cols() / 2
    var ry = small.rows() / 2
    resize(small, small, new Size(rx.toInt, ry.toInt), 0.0, 0.0, INTER_AREA);
    //showImage(small, "7. Small Image")

    small
    // Save the clean image
    //imwrite(cutedName, small)

  }


  //
  // Display image
  //
  def showImage(image:Mat, title:String = "Image for local purpouses") {
    var canvas = new CanvasFrame(title, 1)
    canvas.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    canvas.setCanvasSize(image.cols()/4, image.rows()/4)
    var tmp = new Mat()
    image.convertTo(tmp, CV_8U, 1, 0)
    var converter = new OpenCVFrameConverter.ToMat()
    canvas.showImage(converter.convert(tmp))
  }

}


