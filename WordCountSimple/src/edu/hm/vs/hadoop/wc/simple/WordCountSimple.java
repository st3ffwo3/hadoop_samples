package edu.hm.vs.hadoop.wc.simple;

import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.lib.LongSumReducer;
import org.apache.hadoop.mapred.lib.TokenCountMapper;

/**
 * Hadoop WordCount Simple Beispiel. Es werden ausschließlich Basisklassen der Hadoop API verwendet.
 * 
 * @author Stefan Wörner
 */
public final class WordCountSimple
{

	/**
	 * Privater Konstruktor.
	 */
	private WordCountSimple()
	{

	}

	/**
	 * Main-Methode.
	 * 
	 * @param args
	 *            Aufrufparameter
	 */
	public static void main( String[] args )
	{
		JobClient client = new JobClient();
		JobConf configuration = new JobConf( WordCountSimple.class );

		FileInputFormat.addInputPath( configuration, new Path( args[0] ) );
		FileOutputFormat.setOutputPath( configuration, new Path( args[1] ) );

		configuration.setOutputKeyClass( Text.class );
		configuration.setOutputValueClass( LongWritable.class );
		configuration.setMapperClass( TokenCountMapper.class );
		configuration.setCombinerClass( LongSumReducer.class );
		configuration.setReducerClass( LongSumReducer.class );

		client.setConf( configuration );

		try
		{
			JobClient.runJob( configuration );
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
