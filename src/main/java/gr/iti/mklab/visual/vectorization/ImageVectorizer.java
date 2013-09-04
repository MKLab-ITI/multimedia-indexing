package gr.iti.mklab.visual.vectorization;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;

import gr.iti.mklab.visual.aggregation.DescriptorAggregator;
import gr.iti.mklab.visual.aggregation.VladAggregator;
import gr.iti.mklab.visual.dimreduction.PrincipalComponentAnalysis;
import gr.iti.mklab.visual.extraction.ImageScaling;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.utilities.Normalization;

/**
 * This class is used to ...
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class ImageVectorizer implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final int SURFlength = 64;
	private static final int numCentroids = 64;

	private VladAggregator VLAD;
	private PrincipalComponentAnalysis PCA;

	/**
	 * Initializes an ImageToVectorSimple object which is able to transform an input image into a VLAD+SURF
	 * vector
	 * 
	 * @param codebookFileName
	 *            the file containing the centroids
	 * @param PCAFileName
	 *            the file containing the PCA projection matrix
	 * @param expensive
	 *            if true, the expensive (long) vector is extracted
	 * @throws Exception
	 */
	public ImageVectorizer(String codebookFileName, String PCAFileName, boolean expensive) throws Exception {
		int projectionLength;
		if (expensive) {
			projectionLength = 1024;
		} else {
			projectionLength = 96;
		}
		initiallizeCodebookAndPCA(codebookFileName, PCAFileName, projectionLength);
	}

	public ImageVectorizer(String codebookFileName, String PCAFileName, int projectionLength) throws Exception {
		initiallizeCodebookAndPCA(codebookFileName, PCAFileName, projectionLength);
	}

	public ImageVectorizer(Path codebookFileName, Path PCAFileName, Configuration conf, boolean expensive) throws Exception {
		int projectionLength;
		if (expensive) {
			projectionLength = 1024;
		} else {
			projectionLength = 96;
		}
		initiallizeCodebookAndPCA(codebookFileName, PCAFileName, conf, projectionLength);
	}

	private void initiallizeCodebookAndPCA(Path codebookFileName, Path PCAFileName, Configuration conf, int projectionLength) throws IOException {
		// initialize the VLAD object
		if (codebookFileName != null) {
			double[][] codebook = DescriptorAggregator.readCodebookFile(conf, codebookFileName, numCentroids, SURFlength);
			VLAD = new VladAggregator(codebook);
		}
		// initialize the PCA object
		if (PCAFileName != null && projectionLength < SURFlength * numCentroids) {
			PCA = new PrincipalComponentAnalysis(projectionLength, 1, SURFlength * numCentroids);
			PCA.setPCAFromFile(conf, PCAFileName);
		}
	}

	private void initiallizeCodebookAndPCA(String codebookFileName, String PCAFileName, int projectionLength) throws Exception {
		// initialize the VLAD object
		if (codebookFileName != null) {
			double[][] codebook = DescriptorAggregator.readCodebookFile(codebookFileName, numCentroids, SURFlength);
			VLAD = new VladAggregator(codebook);
		}
		// initialize the PCA object
		if (PCAFileName != null && projectionLength < SURFlength * numCentroids) {
			PCA = new PrincipalComponentAnalysis(projectionLength, 1, SURFlength * numCentroids);
			PCA.setPCAFromFile(PCAFileName);
		}
	}

	/**
	 * Transforms an image file into a vector
	 */
	public double[] transformToVector(String imageFilename) throws Exception {
		// read the image
		BufferedImage image;
		try { // first try reading with the default class
			image = ImageIO.read(new File(imageFilename));
		} catch (Exception e) { // if it fails retry with the corrected class
			// This exception is probably because of a grayscale jpeg image.
			System.out.println("Exception: " + e.getMessage() + " | Image: " + imageFilename);
			try{
			image = readImage(imageFilename);
			}
			catch(Exception ex) {
				System.out.println("Exception: " + ex.getMessage() + " | Image: " + imageFilename);
				image = ImageIOCorrected.read(new File(imageFilename));
			}
		}
		// next the image is down-scaled
		ImageScaling scale = new ImageScaling();
		image = scale.maxPixelsScaling(image);

		// next the descriptors are extracted
		SURFExtractor surf = new SURFExtractor();
		double[][] descriptors = surf.extractDescriptors(image);

		// next the descriptors are aggregated
		double[] vladVector = VLAD.aggregate(descriptors);
		double power = 0.5;
		vladVector = Normalization.normalizePower(vladVector, power);
		vladVector = Normalization.normalizeL2(vladVector);

		// print the VLAD vector
		// System.out.println("VLAD vector:\n" + Arrays.toString(vladVector));

		// next the vector is pca-reduced
		double[] pcaReducedVector = PCA.sampleToEigenSpace(vladVector);
		pcaReducedVector = Normalization.normalizeL2(pcaReducedVector);
		// print the PCA transformed VLAD vector
		// System.out.println("PCA transformed VLAD vector:\n" + Arrays.toString(transformed));
		return pcaReducedVector;
	}

	private BufferedImage readImage(String filename) throws IOException {
		InputStream picture = new FileInputStream(filename);
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[10240];
        int l = 0;
        while ((l = picture.read(b)) >= 0) {
            buf.write(b, 0, l);
        }
        buf.close();
        byte[] picturedata = buf.toByteArray();
        
		ImageInputStream input = ImageIO
                .createImageInputStream(new ByteArrayInputStream(
                        picturedata));
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        ImageReader reader = null;
        while (readers.hasNext()) {
            reader = (ImageReader) readers.next();
            if (reader.canReadRaster())
                break;
        }

        if (reader == null) {
        	picture.close();
            throw new IOException("no reader found");
        }
        // Set the input.
        reader.setInput(input);
        int w = reader.getWidth(0);
        int h = reader.getHeight(0);
        BufferedImage image;
        image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Image intImage = Toolkit.getDefaultToolkit().createImage(
                picturedata);
        new ImageIcon(intImage);
        Graphics2D g = image.createGraphics();
        g.drawImage(intImage, 0, 0, null);
        g.dispose();
        picture.close();
        return image;
	}
	/**
	 * Transforms a BufferedImage into a vector
	 */
	public double[] transformToVector(BufferedImage image) throws Exception {
		// the image is down-scaled
		ImageScaling scale = new ImageScaling();
		image = scale.maxPixelsScaling(image);

		// next the descriptors are extracted
		SURFExtractor surf = new SURFExtractor();
		double[][] descriptors = surf.extractDescriptors(image);

		// next the descriptors are aggregated
		double[] vladVector = VLAD.aggregate(descriptors);
		double power = 0.5;
		vladVector = Normalization.normalizePower(vladVector, power);
		vladVector = Normalization.normalizeL2(vladVector);

		// next the vector is pca-reduced
		double[] pcaReducedVector = PCA.sampleToEigenSpace(vladVector);
		pcaReducedVector = Normalization.normalizeL2(pcaReducedVector);
		// print the PCA transformed VLAD vector
		return pcaReducedVector;
	}

	/**
	 * Transforms a set of descriptors into a vector
	 */
	public double[] transformToVector(double[][] descriptors) throws Exception {
		// next the descriptors are aggregated
		double[] vladVector = VLAD.aggregate(descriptors);
		double power = 0.5;
		vladVector = Normalization.normalizeL2(vladVector);
		vladVector = Normalization.normalizePower(vladVector, power);

		// next the vector is pca-reduced
		double[] pcaReducedVector = PCA.sampleToEigenSpace(vladVector);
		pcaReducedVector = Normalization.normalizeL2(pcaReducedVector);
		// print the PCA transformed VLAD vector
		return pcaReducedVector;
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		// String codebookFileName = "C:/Users/lef/Desktop/SocialSensorService/learning_files/codebook.txt";
		// String PCAFileName = "C:/Users/lef/Desktop/SocialSensorService/learning_files/pca.txt";
		// int vectorLength = 3;
		// ImageVectorizerSimple vectorizer = new ImageVectorizerSimple(codebookFileName, PCAFileName,
		// vectorLength);

		Path codebookFileName = new Path("/image-indexer/codebook.txt");
		Path PCAFileName = new Path("/image-indexer/pca.txt");
		Configuration conf = new Configuration();
		conf.addResource(new Path("C:/Users/lef/Desktop/hadoop.conf.xml"));
		ImageVectorizer vectorizer = new ImageVectorizer(codebookFileName, PCAFileName, conf, false);

		String imagesFolder = "C:/Users/lef/Desktop/ITI/data/Holidays/images/";// "images/ec1m_00010001.jpg";
		File dir = new File(imagesFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg") || name.toLowerCase().endsWith(".png"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		BufferedWriter out = new BufferedWriter(new FileWriter(new File(imagesFolder + "surf.txt")));
		for (int i = 0; i < files.length; i++) {
			long start = System.currentTimeMillis();
			double[] vector = vectorizer.transformToVector(imagesFolder + files[i]);
			out.write(files[i] + " ");
			for (int j = 0; j < vector.length; j++) {
				out.write(vector[j] + " ");
			}
			out.write("\n");
			out.flush();
			System.out.println("Vectorization for image " + files[i] + " completed in " + (System.currentTimeMillis() - start) + " ms.");
		}
		out.close();

	}
}
