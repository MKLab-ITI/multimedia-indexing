package gr.iti.mklab.visual.mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.net.URI;


/**
 * A hadoop visual feature extraction job
 *
 * @author Katerina Andreadou
 */
public class VisualJob extends Configured implements Tool {

    //LOCAL CONFIGURATION
    //public final static String LEARNING_FILES_PATH = "/home/kandreadou/webservice/learning_files/";

    //AMAZON ELASTIC MAPREDUCE CONFIGURATION
    //https://s3.amazonaws.com/gr.iti.mklab/learningfiles/pq_1024_64x8_rp_ivf_8192k.csv
    public final static String LEARNING_FILES_PATH = "";
    public final static boolean IS_LOCAL = false;

    /**
     * Main entry point that uses the {@link org.apache.hadoop.util.ToolRunner} class to run the Hadoop job.
     */
    public static void main(String[] args) throws Exception {

        int res = ToolRunner.run(new Configuration(), new VisualJob(), args);
        System.exit(res);
    }

    @Override
    public int run(String[] args) throws Exception {
        String inputPath = args[0];
        String outputPath = args[1];
        if (!IS_LOCAL & args.length >= 3) {
            String configFile = args[2];
            if (configFile != null) {
                getConf().addResource(configFile);
            }
            //The learning files have to be uploaded to the s3 bucket first
            //Then when starting the job, they have to be added to the hadoop distributed cache
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/surf_l2_128c_0.csv#surf_l2_128c_0.csv"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/surf_l2_128c_1.csv#surf_l2_128c_1.csv"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/surf_l2_128c_2.csv#surf_l2_128c_2.csv"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/surf_l2_128c_3.csv#surf_l2_128c_3.csv"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/pca_surf_4x128_32768to1024.txt#pca_surf_4x128_32768to1024.txt"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/qcoarse_1024d_8192k.csv#qcoarse_1024d_8192k.csv"), getConf());
            DistributedCache.addCacheFile(new URI("s3n://gr-mklab/learningfiles/pq_1024_64x8_rp_ivf_8192k.csv#pq_1024_64x8_rp_ivf_8192k.csv"), getConf());
        }

        Job job = createJob(inputPath, outputPath);
        return job.waitForCompletion(true) ? 0 : -1;
    }

    private Job createJob(String inputPath, String outputPath) throws Exception {
        Configuration conf = getConf();
        Job job = new Job(conf);
        job.setJarByClass(VisualJob.class);
        job.setNumReduceTasks(90);

        FileSystem fs = FileSystem.get(new URI(outputPath), conf);
        if (fs.exists(new Path(outputPath))) {
            fs.delete(new Path(outputPath), true);
        }

        FileInputFormat.setInputPaths(job, new Path(inputPath));
        FileOutputFormat.setOutputPath(job, new Path(outputPath));
        FileOutputFormat.setCompressOutput(job, true);
        FileOutputFormat.setOutputCompressorClass(job, GzipCodec.class);

        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);

        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(FloatArrayWritable.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setMapperClass(VisualThreadedMapper.class);
        job.setReducerClass(VisualReducer.class);

        return job;
    }

    public static float[] castToFloat(double[] doubleArray) {
        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }
}
