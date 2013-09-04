package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;

/**
 * Interface for all descriptor extractors.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public interface DescriptorExtractor {

	public static final int SIFTLength = 128;
	public static final int SURFLength = 64;

	public double[][] extractDescriptors(BufferedImage image) throws Exception;

	public int getTotalDetectionTime();

	public int getTotalDescriptionTime();

	public int getTotalNumberInterestPoints();

	public void setPowerNormalization(boolean normalize);

	public void setL2Normalization(boolean normalize);

}
