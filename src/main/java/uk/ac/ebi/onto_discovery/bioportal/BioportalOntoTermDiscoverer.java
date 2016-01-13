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
import uk.ac.ebi.bioportal.webservice.model.ClassRef;
import uk.ac.ebi.bioportal.webservice.model.OntologyClass;
import uk.ac.ebi.bioportal.webservice.model.TextAnnotation;
import uk.ac.ebi.onto_discovery.api.OntologyDiscoveryException;
import uk.ac.ebi.onto_discovery.api.OntologyTermDiscoverer;

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
		if ( (valueLabel = StringUtils.trimToNull ( valueLabel )) == null ) return NULL_RESULT;
		
		List<DiscoveredTerm> result = getOntologyTermsFromBioportal ( valueLabel );
		// If you fail with the value, try the type instead
		if ( result != NULL_RESULT || typeLabel == null ) return result;
		
		return getOntologyTermsFromBioportal ( typeLabel );
	}
	
	private List<DiscoveredTerm> getOntologyTermsFromBioportal ( String text ) throws OntologyDiscoveryException
	{
		try
		{
			TextAnnotation[] anns;

			// First, try with ontologies of interest, if available
			//
			if ( preferredOntologies == null )
				anns = bpclient.getTextAnnotations ( text, "longest_only", "true" );
			else
			{
				anns = bpclient.getTextAnnotations ( text, "longest_only", "true", "ontologies", preferredOntologies );

				// If that didn't yield a result, try with the rest too, unless the corresponding option says no
				if ( anns.length == 0 && !this.usePreferredOntologiesOnly )
					anns = bpclient.getTextAnnotations ( text, "longest_only", "true" );
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
				
				result.add ( new DiscoveredTerm ( classIri, (Double) null, classLabel, "Bioportal Annotator" ) );
			}
			
			if ( result.size () == 0 ) return NULL_RESULT;
			return result;
		}
		catch ( Exception ex )
		{
			log.error ( String.format ( 
				"Error while invoking Bioportal for '%s': %s. Returning null", text, ex.getMessage () 
			));
			if ( log.isDebugEnabled () ) log.debug ( "Underline exception is:", ex );
			return null;
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
}
