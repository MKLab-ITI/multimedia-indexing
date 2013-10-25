package gr.iti.mklab.visual.utilities;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * Utility class for various feature IO operations.
 * 
 * @author Eleftherios Spyromitros-Xioufis
 * 
 */
public class FeatureIO {

	/**
	 * Reads features from a binary file and returns them in an ArrayList<double[]>.
	 * 
	 * @param featuresFileName
	 *            The binary file containing the features.
	 * @param featureLength
	 *            The length of each feature.
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<double[]> readBinary(String featuresFileName, int featureLength) throws Exception {
		ArrayList<double[]> features = new ArrayList<double[]>();
		DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(featuresFileName)));

		int counter = 0;
		double[] desc = new double[featureLength];
		while (true) {
			try {
				desc[counter] = in.readDouble();
			} catch (EOFException e) {
				break;
			}
			counter++;
			if (counter == featureLength) {
				features.add(desc);
				counter = 0;
				desc = new double[featureLength];
			}
		}
		in.close();
		return features;
	}

	/**
	 * Reads features from a text file and returns them in an ArrayList<double[]>.
	 * 
	 * @param featuresFileName
	 *            The text file containing the features.
	 * @param featureLength
	 *            The length of each feature.
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<double[]> readText(String featuresFileName, int featureLength) throws Exception {
		ArrayList<double[]> features = new ArrayList<double[]>();
		BufferedReader in = new BufferedReader(new FileReader(new File(featuresFileName)));

		String line;
		while ((line = in.readLine()) != null) {
			String[] stringVals = line.split(",");
			if (stringVals.length != featureLength) {
				in.close();
				throw new Exception("Line contains " + stringVals.length + " comma separated values instead of "
						+ featureLength + "\n" + line);
			}
			double[] vals = new double[featureLength];
			for (int j = 0; j < featureLength; j++) {
				vals[j] = Double.parseDouble(stringVals[j]);
			}
			features.add(vals);
		}
		in.close();
		return features;
	}

	/**
	 * Takes a two-dimensional double array with features and writes them in a binary file.
	 * 
	 * @param featuresFileName
	 *            The binary file's name.
	 * @param features
	 *            The features.
	 * @throws Exception
	 */
	public static void writeBinary(String featuresFileName, double[][] features) throws Exception {
		DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(featuresFileName)));
		for (int i = 0; i < features.length; i++) {
			for (int j = 0; j < features[i].length; j++) {
				out.writeDouble(features[i][j]);
			}
		}
		out.close();
	}

	/**
	 * Takes a two-dimensional double array with features and writes them in a comma separated text file.
	 * 
	 * @param featuresFileName
	 *            The text file's name.
	 * @param features
	 *            The features.
	 * @throws Exception
	 */
	public static void writeText(String featuresFileName, double[][] features) throws Exception {
		BufferedWriter out = new BufferedWriter(new FileWriter(featuresFileName));
		for (double[] feature : features) {
			for (int j = 0; j < feature.length - 1; j++) {
				out.write(feature[j] + ",");
			}
			out.write(feature[feature.length - 1] + "\n");
		}
		out.close();
	}

	/**
	 * Takes a folder with feature files in text format and converts them to binary.
	 * 
	 * @param featuresFolder
	 * @param featureType
	 * @param featureLength
	 * @throws Exception
	 */
	public static void textToBinary(String featuresFolder, final String featureType, int featureLength)
			throws Exception {
		// read the folder containing the description files
		File dir = new File(featuresFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith("." + featureType))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		for (int i = 0; i < files.length; i++) {
			if (i % 100 == 0) {
				System.out.println("Converting file " + i);
			}
			BufferedReader in = new BufferedReader(new FileReader(new File(featuresFolder + files[i])));
			DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(featuresFolder
					+ files[i] + "b")));
			String line;
			while ((line = in.readLine()) != null) {
				String[] nums = line.split(",");
				for (int j = 0; j < featureLength; j++) {
					out.writeDouble(Double.parseDouble(nums[j]));
				}
			}
			in.close();
			out.close();
		}
	}

	/**
	 * Takes a folder with feature files in binary format and converts them to text.
	 * 
	 * @param featuresFolder
	 * @param featureType
	 * @param featureLength
	 * @throws Exception
	 */
	public static void binaryToText(String featuresFolder, final String featureType, int featureLength)
			throws Exception {
		// read the folder containing the description files
		File dir = new File(featuresFolder);
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if (name.endsWith("." + featureType + "b"))
					return true;
				else
					return false;
			}
		};
		String[] files = dir.list(filter);

		for (int i = 0; i < files.length; i++) {
			if (i % 100 == 0) {
				System.out.println("Converting file " + i);
			}

			DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(featuresFolder
					+ files[i])));
			BufferedWriter out = new BufferedWriter(
					new FileWriter(new File(featuresFolder + files[i].replace("b", ""))));
			long counter = 0;
			while (true) {
				try {
					double val = in.readDouble();
					out.write(String.valueOf(val));
				} catch (EOFException e) {
					break;
				}
				counter++;
				if (counter % featureLength == 0) {
					out.write("\n");
				} else {
					out.write(",");
				}

			}
			in.close();
			out.close();
		}
	}
}
