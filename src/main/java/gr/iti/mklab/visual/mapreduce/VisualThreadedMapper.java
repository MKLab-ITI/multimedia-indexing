package gr.iti.mklab.visual.mapreduce;

import gr.iti.mklab.download.ImageDownloadResult;
import gr.iti.mklab.download.ImageDownloader;
import gr.iti.mklab.visual.aggregation.AbstractFeatureAggregator;
import gr.iti.mklab.visual.aggregation.VladAggregatorMultipleVocabularies;
import gr.iti.mklab.visual.dimreduction.PCA;
import gr.iti.mklab.visual.extraction.AbstractFeatureExtractor;
import gr.iti.mklab.visual.extraction.SURFExtractor;
import gr.iti.mklab.visual.vectorization.ImageVectorization;
import gr.iti.mklab.visual.vectorization.ImageVectorizationResult;
import gr.iti.mklab.visual.vectorization.ImageVectorizer;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * The VisualThreadedMapper is responsible for reading the image urls, downloading the images
 * and extracting visual features
 *
 * @author Katerina Andreadou
 */
public class VisualThreadedMapper extends Mapper<LongWritable, Text, Text, FloatArrayWritable> {

    private static int targetLengthMax = 1024;
    private static int maxNumPixels = 768 * 512;
    private static int numVectorizationThreads = 2;
    private static int numDownloadThreads = 10;
    private static ImageDownloader downloader;
    private static ImageVectorizer vectorizer;
    private int submittedDownloadsCounter = 0;
    private int minCallInterval = 60;

    // minimum interval between 2 download calls in msec
    long lastDownLoadCall = 0;


    @Override
    public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {

        // if there are still urls to be submitted for download and the downloader's queue is not full and
        // the required interval between 2 calls has passed
        String urlLine = value.toString();
        boolean lineConsumed = false;
        while (!lineConsumed) {
            if (downloader.canAcceptMoreTasks()
                    && (System.currentTimeMillis() - lastDownLoadCall) >= minCallInterval
                    && urlLine != null) {
                // parse a new line from the file
                String imageUrl;
                boolean isVideo = false;
                String[] segments = urlLine.split("\\s+");
                // check if there is an id
                if (segments.length > 1) {
                    // assuming the id is first
                    imageUrl = segments[1];
                } else {
                    imageUrl = urlLine;
                }

                if (segments.length > 2 && "video".equals(segments[2]))
                    isVideo = true;

                String imageId = imageUrl;
                if (isVideo)
                    imageId += "-v";

                downloader.submitHadoopDownloadTask(imageUrl, imageId);
                lastDownLoadCall = System.currentTimeMillis();
                submittedDownloadsCounter++;
                lineConsumed = true;
                System.out.println("Submitted download tasks: " + submittedDownloadsCounter + " ulr:" + imageUrl);
            }

            // if there is still space in the vectorizer's queue try to get an image download result and
            // to submit a new image vectorization task
            if (vectorizer.canAcceptMoreTasks()) {
                ImageDownloadResult imdr = null;
                try {
                    imdr = downloader.getImageDownloadResult();
                } catch (Exception e) {
                    //context.write(new Text(e.getMessage()), new FloatArrayWritable(new float[0]));
                }
                if (imdr != null) {
                    BufferedImage image = imdr.getImage();
                    // String url = download.getUrlStr();
                    String id = imdr.getImageId();
                    vectorizer.submitImageVectorizationTask(id, image);
                } // if a download result was successfully retrieved
            }

            // try to get an image vectorization result and to index the vector
            ImageVectorizationResult imvr = null;
            try {
                imvr = vectorizer.getImageVectorizationResult();
            } catch (Exception e) {
                //context.write(new Text(e.getMessage()), new FloatArrayWritable(new float[0]));
            }
            if (imvr != null && imvr.getImageVector() != null) {
                String name = imvr.getImageName();
                double[] vector = imvr.getImageVector();
                context.write(new Text(name), new FloatArrayWritable(VisualJob.castToFloat(vector)));
            }
        }

    }

    @Override
    protected void cleanup(Context context) throws IOException, InterruptedException {
        super.cleanup(context);
        downloader.shutDown();
        vectorizer.shutDown();
    }

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
        super.setup(context);
        try {
            initialize(context);
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private void initialize(Context context) throws Exception {

        int[] numCentroids = {128, 128, 128, 128};
        int initialLength = numCentroids.length * numCentroids[0] * AbstractFeatureExtractor.SURFLength;

        /*if (!VisualJob.IS_LOCAL) {

            Path files[] = DistributedCache.getLocalCacheFiles(context.getConfiguration());
            for (Path path : files) {
                context.write(new Text(path.toString() + " " + path.getName()), new DoubleArrayWritable(new double[0]));
            }
        }*/

        String[] codebookFiles = {
                VisualJob.LEARNING_FILES_PATH + "surf_l2_128c_0.csv",
                VisualJob.LEARNING_FILES_PATH + "surf_l2_128c_1.csv",
                VisualJob.LEARNING_FILES_PATH + "surf_l2_128c_2.csv",
                VisualJob.LEARNING_FILES_PATH + "surf_l2_128c_3.csv"
        };
        String pcaFile = VisualJob.LEARNING_FILES_PATH + "pca_surf_4x128_32768to1024.txt";

        ImageVectorization.setFeatureExtractor(new SURFExtractor());
        double[][][] codebooks = AbstractFeatureAggregator.readQuantizers(codebookFiles, numCentroids,
                AbstractFeatureExtractor.SURFLength);
        ImageVectorization.setVladAggregator(new VladAggregatorMultipleVocabularies(codebooks));
        if (targetLengthMax < initialLength) {
            PCA pca = new PCA(targetLengthMax, 1, initialLength, true);
            pca.loadPCAFromFile(pcaFile);
            ImageVectorization.setPcaProjector(pca);
        }

        // Initialize the downloader, the vectorizer and the indexer
        downloader = new ImageDownloader("", numDownloadThreads);
        downloader.setSaveOriginal(false);
        downloader.setSaveThumb(false);
        downloader.setFollowRedirects(false);
        vectorizer = new ImageVectorizer("surf", codebookFiles, numCentroids,
                targetLengthMax, pcaFile, true, numVectorizationThreads);

    }
}
