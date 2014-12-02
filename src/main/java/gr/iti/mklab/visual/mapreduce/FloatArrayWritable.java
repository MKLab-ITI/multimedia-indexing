package gr.iti.mklab.visual.mapreduce;

import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A simple FloatArrayWritable
 *
 * @author Katerina Andreadou
 */
public class FloatArrayWritable implements Writable {

    private float[] data;

    //The empty constructor is necessary for the Reducer to be instantiated
    public FloatArrayWritable() {

    }

    public FloatArrayWritable(float[] data) {
        this.data = data;
    }

    public float[] getData() {
        return data;
    }

    public void setData(float[] data) {
        this.data = data;
    }

    public void write(DataOutput out) throws IOException {
        int length = 0;
        if (data != null) {
            length = data.length;
        }

        out.writeInt(length);

        for (int i = 0; i < length; i++) {
            out.writeFloat(data[i]);
        }
    }

    public void readFields(DataInput in) throws IOException {
        int length = in.readInt();

        data = new float[length];

        for (int i = 0; i < length; i++) {
            data[i] = in.readFloat();
        }
    }
}
