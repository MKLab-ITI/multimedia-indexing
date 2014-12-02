package gr.iti.mklab.visual.mapreduce;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.Arrays;

/**
 * The VisualReducer just writes <name, vector> pairs to a text file
 *
 * @author Katerina Andreadou
 */
public class VisualReducer extends Reducer<Text, FloatArrayWritable, Text, Text> {


    @Override
    public void reduce(Text key, Iterable<FloatArrayWritable> list, Context context) throws IOException, InterruptedException {

        if (list.iterator().hasNext()) {
            //This creates a file with <name,vector> pairs
            context.write(key, new Text(Arrays.toString(list.iterator().next().getData())));
        }

    }
}
