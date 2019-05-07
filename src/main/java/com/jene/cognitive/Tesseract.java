//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jene.cognitive;

import com.sun.jna.Pointer;
import com.sun.jna.StringArray;
import com.sun.jna.ptr.PointerByReference;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.imageio.IIOImage;
import net.sourceforge.lept4j.Box;
import net.sourceforge.lept4j.Boxa;
import net.sourceforge.lept4j.Leptonica;
import net.sourceforge.tess4j.*;
import net.sourceforge.tess4j.ITessAPI.ETEXT_DESC;
import net.sourceforge.tess4j.ITessAPI.TessBaseAPI;
import net.sourceforge.tess4j.ITessAPI.TessPageIterator;
import net.sourceforge.tess4j.ITessAPI.TessResultIterator;
import net.sourceforge.tess4j.ITessAPI.TessResultRenderer;
import net.sourceforge.tess4j.ITesseract.RenderedFormat;
import net.sourceforge.tess4j.util.ImageIOHelper;
import net.sourceforge.tess4j.util.LoggHelper;
import net.sourceforge.tess4j.util.PdfUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tesseract extends net.sourceforge.tess4j.Tesseract {
    private static net.sourceforge.tess4j.Tesseract instance;
    private String language = "eng";
    private String datapath = "./";
    private RenderedFormat renderedFormat;
    private int psm;
    private int ocrEngineMode;
    private final Properties prop;
    private final List<String> configList;
    private TessAPI api;
    private TessBaseAPI handle;
    private static final Logger logger = LoggerFactory.getLogger((new LoggHelper()).toString());

    public Tesseract() {
        super();
        this.renderedFormat = RenderedFormat.TEXT;
        this.psm = -1;
        this.ocrEngineMode = 3;
        this.prop = new Properties();
        this.configList = new ArrayList();
    }

    protected TessAPI getAPI() {
        return this.api;
    }

    protected TessBaseAPI getHandle() {
        return this.handle;
    }

    /** @deprecated */
    @Deprecated
    public static synchronized net.sourceforge.tess4j.Tesseract getInstance() {
        if(instance == null) {
            instance = new net.sourceforge.tess4j.Tesseract();
        }

        return instance;
    }

    public void setDatapath(String var1) {
        this.datapath = var1;
    }

    public void setLanguage(String var1) {
        this.language = var1;
    }

    public void setOcrEngineMode(int var1) {
        this.ocrEngineMode = var1;
    }

    public void setPageSegMode(int var1) {
        this.psm = var1;
    }

    public void setHocr(boolean var1) {
        this.renderedFormat = var1?RenderedFormat.HOCR:RenderedFormat.TEXT;
        this.prop.setProperty("tessedit_create_hocr", var1?"1":"0");
    }

    public void setTessVariable(String var1, String var2) {
        this.prop.setProperty(var1, var2);
    }

    public void setConfigs(List<String> var1) {
        this.configList.clear();
        if(var1 != null) {
            this.configList.addAll(var1);
        }

    }

    public String doOCR(File var1) throws TesseractException {
        return this.doOCR((File)var1, (Rectangle)null);
    }

    public String doOCR(File var1, Rectangle var2) throws TesseractException {
        try {
            return this.doOCR(ImageIOHelper.getIIOImageList(var1), var1.getPath(), var2);
        } catch (Exception var4) {
            logger.error(var4.getMessage(), var4);
            throw new TesseractException(var4);
        }
    }

    public String doOCR(BufferedImage var1) throws TesseractException {
        return this.doOCR((BufferedImage)var1, (Rectangle)null);
    }

    public String doOCR(BufferedImage var1, Rectangle var2) throws TesseractException {
        try {
            return this.doOCR(ImageIOHelper.getIIOImageList(var1), var2);
        } catch (Exception var4) {
            logger.error(var4.getMessage(), var4);
            throw new TesseractException(var4);
        }
    }

    public String doOCR(List<IIOImage> var1, Rectangle var2) throws TesseractException {
        return this.doOCR(var1, (String)null, var2);
    }

    public void initOcr(){
        this.init();
        this.setTessVariables();
    }

    public void endOcr(){
        this.dispose();
    }

    public String doOCR(List<IIOImage> var1, String var2, Rectangle var3) throws TesseractException {
        //this.init();
        //this.setTessVariables();

        try {
            StringBuilder var4 = new StringBuilder();
            int var5 = 0;
            Iterator var6 = var1.iterator();

            while(var6.hasNext()) {
                IIOImage var7 = (IIOImage)var6.next();
                ++var5;

                try {
                    this.setImage(var7.getRenderedImage(), var3);
                    var4.append(this.getOCRText(var2, var5));
                } catch (IOException var12) {
                    logger.error(var12.getMessage(), var12);
                }
            }

            if(this.renderedFormat == RenderedFormat.HOCR) {
                var4.insert(0, "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n<html>\n<head>\n<title></title>\n<meta http-equiv=\"Content-Type\" content=\"text/html;charset=utf-8\" />\n<meta name=\'ocr-system\' content=\'tesseract\'/>\n</head>\n<body>\n").append("</body>\n</html>\n");
            }

            String var14 = var4.toString();
            return var14;
        } finally {
            //this.dispose();
        }
    }

    public String doOCR(int var1, int var2, ByteBuffer var3, Rectangle var4, int var5) throws TesseractException {
        return this.doOCR(var1, var2, var3, (String)null, var4, var5);
    }

    public String doOCR(int var1, int var2, ByteBuffer var3, String var4, Rectangle var5, int var6) throws TesseractException {
        this.init();
        this.setTessVariables();

        String var7;
        try {
            this.setImage(var1, var2, var3, var5, var6);
            var7 = this.getOCRText(var4, 1);
        } catch (Exception var11) {
            logger.error(var11.getMessage(), var11);
            throw new TesseractException(var11);
        } finally {
            this.dispose();
        }

        return var7;
    }

    protected void init() {
        this.api = TessAPI.INSTANCE;
        this.handle = this.api.TessBaseAPICreate();
        StringArray var1 = new StringArray((String[])this.configList.toArray(new String[0]));
        PointerByReference var2 = new PointerByReference();
        var2.setPointer(var1);
        this.api.TessBaseAPIInit1(this.handle, this.datapath, this.language, this.ocrEngineMode, var2, this.configList.size());
        if(this.psm > -1) {
            this.api.TessBaseAPISetPageSegMode(this.handle, this.psm);
        }

    }

    protected void setTessVariables() {
        Enumeration var1 = this.prop.propertyNames();

        while(var1.hasMoreElements()) {
            String var2 = (String)var1.nextElement();
            this.api.TessBaseAPISetVariable(this.handle, var2, this.prop.getProperty(var2));
        }

    }

    protected void setImage(RenderedImage var1, Rectangle var2) throws IOException {
        this.setImage(var1.getWidth(), var1.getHeight(), ImageIOHelper.getImageByteBuffer(var1), var2, var1.getColorModel().getPixelSize());
    }

    protected void setImage(int var1, int var2, ByteBuffer var3, Rectangle var4, int var5) {
        int var6 = var5 / 8;
        int var7 = (int)Math.ceil((double)(var1 * var5) / 8.0D);
        this.api.TessBaseAPISetImage(this.handle, var3, var1, var2, var6, var7);
        if(var4 != null && !var4.isEmpty()) {
            this.api.TessBaseAPISetRectangle(this.handle, var4.x, var4.y, var4.width, var4.height);
        }

    }

    protected String getOCRText(String var1, int var2) {
        if(var1 != null && !var1.isEmpty()) {
            this.api.TessBaseAPISetInputName(this.handle, var1);
        }

        Pointer var3 = this.renderedFormat == RenderedFormat.HOCR?this.api.TessBaseAPIGetHOCRText(this.handle, var2 - 1):this.api.TessBaseAPIGetUTF8Text(this.handle);
        String var4 = var3.getString(0L);
        this.api.TessDeleteText(var3);
        return var4;
    }

    public void createDocuments(String var1, String var2, List<RenderedFormat> var3) throws TesseractException {
        this.createDocuments(new String[]{var1}, new String[]{var2}, var3);
    }

    private void createDocuments(String var1, TessResultRenderer var2) throws TesseractException {
        this.api.TessBaseAPISetInputName(this.handle, var1);
        int var3 = this.api.TessBaseAPIProcessPages(this.handle, var1, (String)null, 0, var2);
        if(var3 == 0) {
            throw new TesseractException("Error during processing page.");
        }
    }

    public List<Rectangle> getSegmentedRegions(BufferedImage var1, int var2) throws TesseractException {
        this.init();
        this.setTessVariables();

        ArrayList var16;
        try {
            ArrayList var3 = new ArrayList();
            this.setImage(var1, (Rectangle)null);
            Boxa var4 = this.api.TessBaseAPIGetComponentImages(this.handle, var2, 1, (PointerByReference)null, (PointerByReference)null);
            Leptonica var5 = Leptonica.INSTANCE;
            int var6 = var5.boxaGetCount(var4);

            for(int var7 = 0; var7 < var6; ++var7) {
                Box var8 = var5.boxaGetBox(var4, var7, 2);
                if(var8 != null) {
                    var3.add(new Rectangle(var8.x, var8.y, var8.w, var8.h));
                    PointerByReference var9 = new PointerByReference();
                    var9.setValue(var8.getPointer());
                    var5.boxDestroy(var9);
                }
            }

            PointerByReference var15 = new PointerByReference();
            var15.setValue(var4.getPointer());
            var5.boxaDestroy(var15);
            var16 = var3;
        } catch (IOException var13) {
            logger.error(var13.getMessage(), var13);
            throw new TesseractException(var13);
        } finally {
            this.dispose();
        }

        return var16;
    }

    public List<Word> getWords(BufferedImage var1, int var2) {
        this.init();
        this.setTessVariables();
        ArrayList var3 = new ArrayList();

        ArrayList var5;
        try {
            this.setImage(var1, (Rectangle)null);
            this.api.TessBaseAPIRecognize(this.handle, (ETEXT_DESC)null);
            TessResultIterator var4 = this.api.TessBaseAPIGetIterator(this.handle);
            TessPageIterator var23 = this.api.TessResultIteratorGetPageIterator(var4);
            this.api.TessPageIteratorBegin(var23);

            do {
                Pointer var6 = this.api.TessResultIteratorGetUTF8Text(var4, var2);
                String var7 = var6.getString(0L);
                this.api.TessDeleteText(var6);
                float var8 = this.api.TessResultIteratorConfidence(var4, var2);
                IntBuffer var9 = IntBuffer.allocate(1);
                IntBuffer var10 = IntBuffer.allocate(1);
                IntBuffer var11 = IntBuffer.allocate(1);
                IntBuffer var12 = IntBuffer.allocate(1);
                this.api.TessPageIteratorBoundingBox(var23, var2, var9, var10, var11, var12);
                int var13 = var9.get();
                int var14 = var10.get();
                int var15 = var11.get();
                int var16 = var12.get();
                Word var17 = new Word(var7, var8, new Rectangle(var13, var14, var15 - var13, var16 - var14));
                var3.add(var17);
            } while(this.api.TessPageIteratorNext(var23, var2) == 1);

            ArrayList var24 = var3;
            return var24;
        } catch (Exception var21) {
            var5 = var3;
        } finally {
            this.dispose();
        }

        return var5;
    }

    protected void dispose() {
        this.api.TessBaseAPIDelete(this.handle);
    }
}
