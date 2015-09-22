package uk.ac.ebi.onto_discovery.bioportal;

import static uk.ac.ebi.onto_discovery.api.CachedOntoTermDiscoverer.NULL_RESULT;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.bioportal.webservice.client.BioportalClient;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation.ClassRef;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;
import uk.ac.ebi.utils.time.XStopWatch;

/**
 * Ontology Discoverer based on <a href = 'https://bioportal.bioontology.org/annotator'>Bioportal Annotator</a>.
 *
 * @author brandizi
 * <dl><dt>Date:</dt><dd>18 Sep 2015</dd>
 *
 */
public class BioportalOntoTermDiscoverer extends OntologyTermDiscoverer
{
	private BioportalClient bpclient;
	private String preferredOntologies;
	private boolean fetchLabels = false;
	private boolean usePreferredOntologiesOnly = false;
	
	
	private long minCallDelay = 0;
	private int totalCalls = 0, failedCalls = 0;
	private long statsSamplingTime = 5 * 60 * 1000; 
		
	private XStopWatch statsTimer = new XStopWatch ();

	
	private Logger log = LoggerFactory.getLogger ( this.getClass () );
	
	/**
	 * This is the easiest way to initialise me. You should create an account on BioPortal and gets an API key. 
	 * PLEASE do not use the ones coming from our code.
	 *  
	 */
	public BioportalOntoTermDiscoverer ( String bioportalApiKey )
	{
		this ( new BioportalClient ( bioportalApiKey ) );
	}
	
	public BioportalOntoTermDiscoverer ( BioportalClient bpClient )
	{
		this.bpclient = bpClient;
	}

	@Override
	public List<DiscoveredTerm> getOntologyTerms ( String valueLabel, String typeLabel ) throws OntologyDiscoveryException
	{
		if ( this.statsSamplingTime > 0 && statsTimer.isStopped () ) statsTimer.start ();
		long callStartTime = this.minCallDelay == 0 ? 0 : System.currentTimeMillis ();			
		
		try
		{
			if ( (valueLabel = StringUtils.trimToNull ( valueLabel )) == null ) return NULL_RESULT;

			TextAnnotation[] anns;

			// First, try with ontologies of interest, if available
			//
			if ( preferredOntologies == null )
				anns = bpclient.getTextAnnotations ( valueLabel, "longest_only", "true" );
			else
			{
				anns = bpclient.getTextAnnotations ( valueLabel, "longest_only", "true", "ontologies", preferredOntologies );

				// If that didn't yield result, try with the rest too, unless the corresponding option says no
				if ( anns.length == 0 && !this.usePreferredOntologiesOnly )
					anns = bpclient.getTextAnnotations ( valueLabel, "longest_only", "true" );
			}
			
			// Collect the results 
			//
			
			if ( anns.length == 0 ) return NULL_RESULT;
			
			Set<String> visitedIris = new HashSet<> ();
			
			List<DiscoveredTerm> result = new ArrayList<> ( anns.length );
			for ( TextAnnotation ta: anns )
			{
				ClassRef classRef = ta.getAnnotatedClass ();
				if ( classRef == null ) continue;
				String classIri = classRef.getClassIri ();
				if ( visitedIris.contains ( classIri ) ) continue;
				visitedIris.add ( classIri );
				
				String classLabel = null; 
				
				if ( fetchLabels )
				{
					OntologyClass ontoClass = bpclient.getOntologyClass ( classRef.getOntologyAcronym (), classIri );
					if ( ontoClass == null ) continue;
					classLabel = ontoClass.getPreferredLabel ();
				}
				
				result.add ( new DiscoveredTerm ( classIri, (Double) null, classLabel ) );
			}
			
			if ( result.size () == 0 ) return NULL_RESULT;
			return result;
		}
		catch ( Exception ex )
		{
			if ( this.statsSamplingTime > 0 ) this.failedCalls++;
			log.error ( String.format ( 
				"Error while invoking Bioportal for '%s':'%s': %s. Returning null", valueLabel, typeLabel, ex.getMessage () 
			));
			if ( log.isDebugEnabled () ) log.debug ( "Underline exception is:", ex );
			return null;
		}
		finally 
		{
			if ( this.statsSamplingTime > 0 ) this.totalCalls++;
			doMinCallDelay ( callStartTime );
			doStats ();
		}
	}

	/**
	 * Delays the call to {@link #getOntologyTerms(String, String)} by the {@link #getMinCallDelay()} time.
	 */
	private boolean doMinCallDelay ( long callStartTime )
	{
		try 
		{
			if ( this.minCallDelay == 0 ) return false;
			
			long callTime = System.currentTimeMillis () - callStartTime;
			long deltaDelay = this.minCallDelay - callTime;
		
			if ( deltaDelay <= 0 ) return false;
			
			log.trace ( "Sleeping for {} ms, due to minCallDelay of {}", deltaDelay, minCallDelay );
			Thread.sleep ( deltaDelay );
			return true;
		}
		catch ( InterruptedException e ) {
			throw new RuntimeException ( "Internal error with Thread.sleep(): " + e.getMessage (), e );
		}
	}
	
	
	/**
	 * Performs Bioportal performance statistics and log them with INFO level. This is invoked after 
	 * {@link #getOntologyTerms(String, String)} and after {@link #getStatsSamplingTime()} ms.
	 * 
	 */
	private void doStats ()
	{
		if ( this.statsSamplingTime <= 0 ) return;
			
		synchronized ( statsTimer )
		{
			if ( statsTimer.getTime () < statsSamplingTime ) return;
			
			long time = statsTimer.getTime ();
			
			double failedPercent = this.totalCalls == 0 ? 0d : 100d * this.failedCalls / this.totalCalls;
			double speed = 60 * 1000d * this.totalCalls / time; 

			log.info ( String.format ( 
				"---- BioPortal Statistics, throughput: %.0f calls/min, failed: %.1f %%", speed, failedPercent 
			)); 
			
			this.totalCalls = this.failedCalls = 0;
			statsTimer.restart ();
		}

	}
	
	
	public BioportalClient getBioportalClient ()
	{
		return bpclient;
	}

	public void setBioportalClient ( BioportalClient bpclient )
	{
		this.bpclient = bpclient;
	}

	/**
	 * @return The ontology acronyms to be used by {@link #getOntologyTerms(String, String)}, either as first-choice, or
	 * as the only sources. These are the acronyms used internally by Bioportal.
	 * @see {@link #usePreferredOntologiesOnly()}. 
	 */
	public String getPreferredOntologies ()
	{
		return preferredOntologies;
	}

	public void setPreferredOntologies ( String preferredOntologies )
	{
		this.preferredOntologies = preferredOntologies;
	}
	
	/**
	 * True if you want to use {@link #getPreferredOntologies()} only for {@link #getOntologyTerms(String, String)}.
	 */
	public boolean usePreferredOntologiesOnly ()
	{
		return usePreferredOntologiesOnly;
	}
	
	public void setUsePreferredOntologiesOnly ( boolean usePreferredOntologiesOnly )
	{
		this.usePreferredOntologiesOnly = usePreferredOntologiesOnly;
	}

	/**
	 * True if you want to fetch preferred labels from ontology terms. This makes {@link #getOntologyTerms(String, String)}
	 * slower, but usually the cache cope with it. 
	 */
	public boolean fetchLabels ()
	{
		return fetchLabels;
	}

	public void setFetchLabels ( boolean fetchLabels )
	{
		this.fetchLabels = fetchLabels;
	}

	/**
	 * <p>Bioportal server doesn't like to be hammered with too many requests, so you can set this to non-zero, to obtain
	 * that {@link #getOntologyTerms(String, String)} lasts this value at least. Default is 0.</p>
	 *  
	 * <p>According to our experience and Bioportal people, the server starts issuing errors like HTTP/429 at speeds &gt; 15
	 * calls/s per process/JVM. This is 66.67ms and we suggest this limit, if you perform many requests in sequence. 
	 * You have to further adjust this time, if you work in multi-thread mode.</p>  
	 */
	public long getMinCallDelay ()
	{
		return minCallDelay;
	}

	public void setMinCallDelay ( long minCallDelay )
	{
		this.minCallDelay = minCallDelay;
	}

	/**
	 * Calls {@link #doStats()} every this time, in ms.
	 */
	public long getStatsSamplingTime ()
	{
		return statsSamplingTime;
	}

	public void setStatsSamplingTime ( long statsSamplingTime )
	{
		this.statsSamplingTime = statsSamplingTime;
	}
	
}
