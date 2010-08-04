package org.crossref.pdfstamp;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.pdf.PdfAction;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfDocument;
import com.itextpdf.text.pdf.PdfObject;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;

// -u "http://blah.com" -i somefile.jpeg -s 1,44.5,22.3,3,22.2,22.2 some/dir
//                                                          or some.file

// or:
//-u "http://blah.com" -i somefile.jpeg -s 1,44.5,22.3 -s 3,22.2,22.2 some/dir
//                                                          or some.file
 class Main {
    
    @Option(name="-p", usage="Page number of page to stamp.",
            required=false, multiValued=false)
    private int page = 1;
    
    @Option(name="-l", usage="Location on page to apply stamp.",
            required=true, multiValued=true)
    private List<StampTuple> stampLocations = new ArrayList<StampTuple>();
    
    @Option(name="-r", usage="Descend recursively into directories.")
    private boolean recursive = false;
    
    @Option(name="-u", usage="Target URL of the stamp.", 
            required=false, multiValued=false)
    private String url = "";
    
    @Option(name="-i", usage="Image file containing image of the stamp.", 
            required=true, multiValued=false)
    private File imageFile = new File(".");
    
    @Option(name="-d", usage="Output directory.",
            required=false, multiValued=false)
    private File outputDirectory = null;
    
    @Argument
    private List<String> paths = new ArrayList<String>();
    
    private Image stampImage = null;
    
    private static Image openImage(File f) throws BadElementException, 
            MalformedURLException, IOException {
        return Image.getInstance(f.getAbsolutePath());
    }
    
    private static PdfReader openPdf(File f) throws IOException {
        FileInputStream fIn = new FileInputStream(f);
        PdfReader reader = new PdfReader(fIn);
        fIn.close();
        return reader;
    }
    
    private static void closePdf(PdfReader r) {
        r.close();
    }
    
    private static PdfStamper openStamper(File f, PdfReader r) 
            throws DocumentException, IOException {
        FileOutputStream fOut = new FileOutputStream(f);
        PdfStamper stamper = new PdfStamper(r, fOut);
        return stamper;
    }
    
    private static void stampPdf(PdfStamper s, Image i, String url,
                                 float x, float y, int page) 
            throws DocumentException {
        PdfContentByte content = s.getOverContent(page);
        content.saveState();
        content.addImage(i, i.getWidth(), 0.0f, 0.0f, i.getHeight(), x, y);
        content.setAction(new PdfAction(url), 
                          x, 
                          y + i.getHeight(), 
                          x + i.getWidth(), 
                          y);
        content.restoreState();
    }
    
    private static void closeStamper(PdfStamper s) throws DocumentException, 
            IOException {
        s.close();
    }
    
    private void addStampsToFile(File in, File out) {
        try {
            PdfReader r = openPdf(in);
            PdfStamper s = openStamper(out, r);
            Image i = openImage(imageFile);
            for (StampTuple st : stampLocations) {
                stampPdf(s, i, url, st.x, st.y, st.page);
            }
            closeStamper(s);
            closePdf(r);
        } catch (Exception e) {
            System.err.println("\033[31m!!\033[30m Failed on " + in.getPath() 
                                    + " because of:");
            System.err.println(e);
        }
    }
    
    private File getOutFileForInFile(File in) {
        if (outputDirectory != null) {
            return new File(outputDirectory.getPath() + File.separator 
                    + in.getName() + ".out");
        } else {
            return new File(in.getPath() + ".out");
        }
    }
    
    public static final void main(String... args) {
        new Main().doMain(args);
    }
    
    private void doMain(String... args) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
            
            try {
                stampImage = openImage(imageFile);
            } catch (Exception e) {
                System.err.println("Couldn't open image file because of:");
                System.err.println(e);
                System.exit(0);
            }
            
            for (String path : paths) {
                File pathFile = new File(path);
                if (pathFile.isDirectory()) {
                    iterateDirectory(pathFile);
                } else {
                    addStampsToFile(pathFile, 
                                    getOutFileForInFile(pathFile));
                }
            }
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
        }
    }
    
    private void iterateDirectory(File dir) {
        for (String path : dir.list()) {
            File pathFile = new File(dir.getPath() + File.separator + path);
            if (pathFile.isDirectory() && recursive) {
                iterateDirectory(pathFile);
            } else {
                addStampsToFile(pathFile,
                                getOutFileForInFile(pathFile));
            }
        }
    }
}