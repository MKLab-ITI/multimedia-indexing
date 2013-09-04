package gr.iti.mklab.visual.extraction;

import java.awt.image.BufferedImage;

import gr.iti.mklab.visual.utilities.Normalization;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.core.image.ConvertBufferedImage;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.struct.feature.SurfFeature;
import boofcv.struct.image.ImageFloat32;

/**
 * This class used the BOOFCV library for extracting SIFT features.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class SIFTExtractor implements DescriptorExtractor {

	private int totalDetectionTime;
	private int totalDescriptionTime;
	private int totalNumberInterestPoints;

	private boolean doubleInputImaged = false;

	private boolean powerNormalization = false;

	public void setPowerNormalization(boolean powerNormalization) {
		this.powerNormalization = powerNormalization;
	}

	private boolean l2Normalization = false;

	public void setL2Normalization(boolean l2Normalization) {
		this.l2Normalization = l2Normalization;
	}

	public SIFTExtractor(boolean doubleInputImaged) {
		this.doubleInputImaged = doubleInputImaged;
	}

	@Override
	public double[][] extractDescriptors(BufferedImage image) {
		long start = System.currentTimeMillis();
		ImageFloat32 boofcvImage = ConvertBufferedImage.convertFromSingle(image, null, ImageFloat32.class);
		ConvertBufferedImage.convertFrom(image, boofcvImage);

		DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribe.sift(4, 1, doubleInputImaged, -1);
		// DetectDescribePoint<ImageFloat32, SurfFeature> sift = FactoryDetectDescribeNormalization.sift(4, 1,
		// doubleInputImaged, -1);

		// specify the image to process
		sift.detect(boofcvImage);

		int numPoints = sift.getNumberOfFeatures();
		double[][] descriptions = new double[numPoints][SIFTLength];
		for (int i = 0; i < numPoints; i++) {
			descriptions[i] = sift.getDescriptor(i).getValue();
			if (powerNormalization) {
				descriptions[i] = Normalization.normalizePower(descriptions[i], 0.5);
			}
			if (l2Normalization) {
				descriptions[i] = Normalization.normalizeL2(descriptions[i]);
			}
		}
		totalNumberInterestPoints += numPoints;
		// System.out.println("Found Features: " + surf.getNumberOfFeatures());
		// System.out.println("First descriptor's first value: " + surf.getDescriptor(0).value[0]);
		totalDescriptionTime += (System.currentTimeMillis() - start);
		return descriptions;
	}

	public int getTotalDetectionTime() {
		return totalDetectionTime;
	}

	public int getTotalDescriptionTime() {
		return totalDescriptionTime;
	}

	public int getTotalNumberInterestPoints() {
		return totalNumberInterestPoints;
	}

}
