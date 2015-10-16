package uk.ac.ebi.bioportal.webservice.client;

import static uk.ac.ebi.bioportal.webservice.client.BioportalClientTest.API_KEY;
import static uk.ac.ebi.bioportal.webservice.utils.BioportalWebServiceUtils.invokeBioportal;

import org.apache.commons.lang3.StringUtils;
import org.junit.Ignore;
import org.junit.Test;

import uk.ac.ebi.bioportal.webservice.exceptions.OntologyServiceException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * For each ontology in Biportal, tries to compute the URL prefix that is used to build the URIs of the classes
 * belonging to the ontology.
 * 
 * It does that by means of some heurisitics, in the sense that it gets the first 5 classes of the ontology and
 * check if they all use the same prefix for their URIs. If not, it reports the problem.
 * 
 * This is used to build {@link BioportalClient#KNOWN_ONTOLOGY_CLASS_URI_PREFIXES}.
 *
 * <dl><dt>date</dt><dd>1 Oct 2014</dd></dl>
 * @author Marco Brandizi
 *
 */
public class GetOntoPrefixesTest
{
	@Test @Ignore ( "It's just some rough code, not a real test" )
	public void getOntoPrefixes ()
	{
		// All the ontologies
		JsonNode jontos = invokeBioportal ( "/ontologies", API_KEY );
		for ( JsonNode jonto: jontos )
		{
			try {
				String ontoId = jonto.get ( "acronym" ).asText ();
				// Skip if we already know this guy
				// You may want to disable this, for updating purposes
				if ( BioportalClient.KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.get ( ontoId ) != null )
				{
					System.out.printf ( "%s\t%s (configured)\n", ontoId, BioportalClient.KNOWN_ONTOLOGY_CLASS_URI_PREFIXES.get ( ontoId ) );
					continue;
				}
				
				// Get the first 5 classes
				JsonNode jclasses = invokeBioportal ( "/ontologies/" + ontoId + "/classes", API_KEY, "pagesize", "5" );
				jclasses = jclasses.get ( "collection" );
				
				// See if they have all the same URI prefix
				String classUriPrefix = null;
				for ( JsonNode jclass: jclasses )
				{
					String classUri = jclass.get ( "@id" ).asText ();
				
					if ( classUri == null ) continue;
					
					// Get the prefix
					String thisClassUriPrefix = null;
					int brkIdx = classUri.lastIndexOf ( '#' );
					if ( brkIdx == -1 )	brkIdx = classUri.lastIndexOf ( '/' );
					if ( brkIdx == -1 ) continue;
					
					thisClassUriPrefix = classUri.substring ( 0, brkIdx + 1 );
					
					if ( classUriPrefix == null ) 
						classUriPrefix = thisClassUriPrefix;
					else if ( !classUriPrefix.equals ( thisClassUriPrefix ) ) 
					{	
						System.err.println ( 
							"WARNING: Multiple class URI prefixes found for '" + ontoId + "', ignoring this ontology"
						);
						classUriPrefix = null;
						break;
					}
				}
				
				System.out.printf ( "%s\t%s\n", ontoId, classUriPrefix );
			}
			catch ( OntologyServiceException ex )
			{
				System.err.println ( 
					"ERROR while getting info about '" + StringUtils.abbreviate ( jonto.toString (), 20 ) 
					 + "': " + ex.getMessage () + ", ignoring this ontology"
				);
			}
		}
	}
}
