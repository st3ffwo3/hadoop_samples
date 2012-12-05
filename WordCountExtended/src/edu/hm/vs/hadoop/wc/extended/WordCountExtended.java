package edu.hm.vs.hadoop.wc.extended;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

/**
 * Hadoop WordCount Extended Beispiel.
 * 
 * @author Stefan Wörner
 */
public class WordCountExtended extends Configured implements Tool
{

	private static final String JOB_NAME = "WordCountExtended";

	private static final String JOB_SKIP_ARGUMENT = "-skip";

	private static final String JOB_PARAMETER_SKIP_PATTERNS = "wordcount.skip.patterns";

	private static final String JOB_PARAMETER_CASE_SENSITIVE = "wordcount.case.sensitive";

	/**
	 * Implementierung des Mappers.
	 * 
	 * @author Stefan Wörner
	 */
	public static class Map extends MapReduceBase implements Mapper<LongWritable, Text, Text, IntWritable>
	{

		/**
		 * Enumeration für Counter.
		 * 
		 * @author Stefan Wörner
		 */
		static enum Counters
		{
			/**
			 * Zähler für die bereits verarbeiteten Wörter.
			 */
			INPUT_WORDS
		}

		private static final IntWritable ONE = new IntWritable( 1 );

		private static final String MAP_PARAMETER_INPUT_FILE = "map.input.file";

		private Text m_word = new Text();

		private boolean m_caseSensitive = true;

		private Set<String> m_patternsToSkip = new HashSet<String>();

		private long m_numRecords = 0;

		private String m_inputFile;

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.apache.hadoop.mapred.MapReduceBase#configure(org.apache.hadoop.mapred.JobConf)
		 */
		@Override
		public void configure( JobConf job )
		{
			m_caseSensitive = job.getBoolean( JOB_PARAMETER_CASE_SENSITIVE, true );
			m_inputFile = job.get( MAP_PARAMETER_INPUT_FILE );

			if (job.getBoolean( JOB_PARAMETER_SKIP_PATTERNS, false ))
			{
				Path[] patternsFiles = new Path[0];
				try
				{
					patternsFiles = DistributedCache.getLocalCacheFiles( job );
				}
				catch (IOException ioe)
				{
					System.err.println( "Caught exception while getting cached files: " + StringUtils.stringifyException( ioe ) );
				}
				for (Path patternsFile : patternsFiles)
				{
					parseSkipFile( patternsFile );
				}
			}
		}

		private void parseSkipFile( Path patternsFile )
		{
			BufferedReader reader = null;

			try
			{
				reader = new BufferedReader( new FileReader( patternsFile.toString() ) );
				String pattern = null;
				while ((pattern = reader.readLine()) != null)
				{
					m_patternsToSkip.add( pattern );
				}
			}
			catch (IOException ioe)
			{
				System.err.println( "Caught exception while parsing the cached file '" + patternsFile + "' : "
						+ StringUtils.stringifyException( ioe ) );
			}
			finally
			{
				try
				{
					reader.close();
				}
				catch (IOException e)
				{
					// Do nothing
					return;
				}
			}
		}

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.apache.hadoop.mapred.Mapper#map(java.lang.Object, java.lang.Object,
		 *      org.apache.hadoop.mapred.OutputCollector, org.apache.hadoop.mapred.Reporter)
		 */
		@Override
		public void map( LongWritable key, Text value, OutputCollector<Text, IntWritable> output, Reporter reporter )
				throws IOException
		{
			String line = m_caseSensitive ? value.toString() : value.toString().toLowerCase();

			for (String pattern : m_patternsToSkip)
			{
				line = line.replaceAll( pattern, "" );
			}

			StringTokenizer tokenizer = new StringTokenizer( line );
			while (tokenizer.hasMoreTokens())
			{
				m_word.set( tokenizer.nextToken() );
				output.collect( m_word, ONE );
				reporter.incrCounter( Counters.INPUT_WORDS, 1 );
			}

			if ((++m_numRecords % 100) == 0)
			{
				reporter.setStatus( "Finished processing " + m_numRecords + " records " + "from the input file: " + m_inputFile );
			}
		}
	}

	/**
	 * Implementierung des Reducers.
	 * 
	 * @author Stefan Wörner
	 */
	public static class Reduce extends MapReduceBase implements Reducer<Text, IntWritable, Text, IntWritable>
	{

		/**
		 * {@inheritDoc}
		 * 
		 * @see org.apache.hadoop.mapred.Reducer#reduce(java.lang.Object, java.util.Iterator,
		 *      org.apache.hadoop.mapred.OutputCollector, org.apache.hadoop.mapred.Reporter)
		 */
		@Override
		public void reduce( Text key, Iterator<IntWritable> values, OutputCollector<Text, IntWritable> output, Reporter reporter )
				throws IOException
		{
			int sum = 0;
			while (values.hasNext())
			{
				sum += values.next().get();
			}
			output.collect( key, new IntWritable( sum ) );
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.apache.hadoop.util.Tool#run(java.lang.String[])
	 */
	@Override
	public int run( String[] args ) throws Exception
	{
		JobConf configuration = new JobConf( getConf(), WordCountExtended.class );
		configuration.setJobName( JOB_NAME );

		configuration.setOutputKeyClass( Text.class );
		configuration.setOutputValueClass( IntWritable.class );

		configuration.setMapperClass( Map.class );
		configuration.setCombinerClass( Reduce.class );
		configuration.setReducerClass( Reduce.class );

		configuration.setInputFormat( TextInputFormat.class );
		configuration.setOutputFormat( TextOutputFormat.class );

		List<String> otherArgs = new ArrayList<String>();
		for (int i = 0; i < args.length; ++i)
		{
			if (JOB_SKIP_ARGUMENT.equals( args[i] ))
			{
				DistributedCache.addCacheFile( new Path( args[++i] ).toUri(), configuration );
				configuration.setBoolean( JOB_PARAMETER_SKIP_PATTERNS, true );
			}
			else
			{
				otherArgs.add( args[i] );
			}
		}

		FileInputFormat.setInputPaths( configuration, new Path( otherArgs.get( 0 ) ) );
		FileOutputFormat.setOutputPath( configuration, new Path( otherArgs.get( 1 ) ) );

		JobClient.runJob( configuration );
		return 0;
	}

	/**
	 * Main-Methode.
	 * 
	 * @param args
	 *            Aufrufparameter
	 * @throws Exception
	 *             Fehler in der Verarbeitung aufgetreten
	 */
	public static void main( String[] args ) throws Exception
	{
		int res = ToolRunner.run( new Configuration(), new WordCountExtended(), args );
		System.exit( res );
	}
}
